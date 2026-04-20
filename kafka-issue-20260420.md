# Kafka 적립 폭주 이슈 정리 (2026-04-20)

## 상황

한 명의 회원이 짧은 시간에 100개의 포인트 적립 요청을 보내는 경우를 기준으로 정리한다.

현재 방향은 `memberId`를 Kafka message key로 사용해 같은 회원의 적립 요청을 같은 partition에 넣고, Consumer가 offset 순서대로 DB에 반영하는 방식이다.

## 1. 중복 적립

문제:
- 클라이언트 재시도, API 재시도, Kafka producer/consumer 재시도로 같은 적립 요청이 두 번 이상 처리될 수 있다.
- Kafka의 idempotent producer는 broker append 중복을 줄여주지만, 비즈니스 요청 중복까지 완전히 막지는 못한다.

해결:
- `requestId`를 멱등성 키로 사용한다.
- `PointLedger.requestId`에 unique 제약을 둔다.
- `EarnPointProcessor`에서 처리 전에 `findByRequestId`로 이미 처리된 요청이면 skip한다.
- DB unique 제약을 최종 방어선으로 본다.

관련 코드:
- `PointController`: requestId가 없으면 UUID 생성
- `EarnPointProcessor`: requestId 중복 확인
- `PointLedger`: request_id unique 컬럼

## 2. Hot key와 partition 병목

문제:
- 같은 `memberId`는 같은 partition으로 들어가므로 한 회원의 100건은 순서대로 처리된다.
- 이 방식은 회원별 순서 보장에는 유리하지만, 특정 회원이 많은 요청을 보내면 해당 partition이 밀릴 수 있다.
- 같은 partition에 배치된 다른 회원의 요청도 뒤에서 대기할 수 있다.

해결:
- producer key를 `memberId`로 유지해 회원별 순서를 우선 보장한다.
- topic partition 수와 consumer concurrency를 같은 값으로 유지한다.
- 운영에서는 특정 회원의 과도한 요청에 API rate limit, abuse detection, 대기열 모니터링을 추가해야 한다.

관련 코드:
- `PointEventProducer`: key = memberId
- `KafkaConfig`: `PARTITION_COUNT = 3`, listener concurrency = partition count

## 3. DB 잔액 갱신 경쟁

문제:
- Kafka 순서 보장이 깨지는 경로가 생기거나, Kafka 외의 다른 API가 같은 지갑을 직접 수정하면 같은 wallet row를 동시에 갱신할 수 있다.
- 동시에 현재 잔액을 읽고 각각 update하면 lost update가 발생할 수 있다.

해결:
- `PointWalletJpaRepository.findByMemberId`에 `PESSIMISTIC_WRITE` 락을 둔다.
- `PointWallet`에 `@Version`도 유지해 낙관적 락 기반 보호도 가능하게 한다.
- Consumer 내부에서 별도 thread pool으로 넘겨 병렬 DB 저장하지 않는다.

관련 코드:
- `PointWalletJpaRepository`: `@Lock(LockModeType.PESSIMISTIC_WRITE)`
- `PointWallet`: `@Version`
- `PointEarnKafkaConsumer`: listener thread에서 직접 처리

## 4. 최대 보유 한도 검증 오류

문제:
- 현재 잔액이 90,000이고 1,000 포인트 적립 100건이 들어오면, 최대 보유 한도가 100,000일 때 앞의 10건만 성공해야 한다.
- 병렬 처리되면 여러 요청이 같은 잔액을 보고 모두 성공 판단할 수 있다.

해결:
- 지갑 row lock을 잡은 상태에서 매 요청마다 최신 잔액 기준으로 `wallet.earn`을 호출한다.
- `EarnPolicy`로 1회 적립 한도와 최대 보유 한도를 검증한다.
- 한도 초과는 비즈니스 실패로 보고 재시도 대상에서 제외한다.

관련 코드:
- `EarnPointProcessor`: 정책 조회 후 `wallet.earn`
- `PointWallet.earn`: 1회 한도, 최대 보유 한도 검증
- `KafkaConfig`: `PointMaxHoldExceededException` not-retryable 등록

## 5. 원장과 지갑 잔액 불일치

문제:
- 원장 insert와 지갑 balance update 중 하나만 성공하면 잔액과 이력이 불일치한다.
- DB commit 후 Kafka ack 전에 consumer가 죽으면 같은 메시지가 다시 처리될 수 있다.

해결:
- `EarnPointProcessor` 전체를 `@Transactional`로 묶는다.
- 원장 저장과 지갑 저장을 같은 transaction 안에서 처리한다.
- Consumer는 DB 처리 완료 후에만 ack한다.
- commit 후 ack 전 장애로 재처리되면 `requestId` 멱등 처리로 중복 반영을 막는다.

관련 코드:
- `EarnPointProcessor`: `@Transactional`
- `PointEarnKafkaConsumer`: `earnPointProcessor.process` 이후 `ack.acknowledge`
- `PointLedger.requestId`: unique 제약

## 6. DLQ가 순서를 깨뜨릴 수 있음

문제:
- 100건 중 37번째가 실패해서 DLQ로 이동하면 38번째 이후는 계속 처리된다.
- 따라서 DLQ로 빠진 메시지는 원래 순서대로 자동 복원되지 않는다.

해결:
- 비즈니스 실패와 시스템 실패를 구분한다.
- 한도 초과처럼 재시도해도 성공하지 않을 예외는 즉시 DLQ로 보내 partition 정체를 줄인다.
- DB 일시 장애 같은 시스템 실패는 retry 후 DLQ로 보낸다.
- DLQ 재처리는 운영자가 원인을 확인하고, 같은 requestId 멱등성을 유지한 상태로 수행해야 한다.

관련 코드:
- `KafkaConfig`: `DefaultErrorHandler`, `DeadLetterPublishingRecoverer`
- `KafkaConfig`: `addNotRetryableExceptions`

## 7. API에서 Kafka publish 전 폭주

문제:
- API가 Kafka 저장 성공을 확인하지 않고 `202 Accepted`를 반환하면, 브로커 전송 실패 후에도 사용자는 접수 성공으로 인식한다.
- 트래픽이 몰릴 때 producer buffer, broker 장애, timeout이 발생할 수 있다.

해결:
- `PointEventProducer`에서 Kafka send 결과를 timeout 내에 확인한다.
- broker 저장 성공 후에만 API가 `202 Accepted`를 반환한다.
- 실패하면 예외를 던져 접수 실패로 처리한다.
- 장기적으로는 outbox table을 두고 API transaction과 메시지 발행을 분리하는 방식도 고려한다.

관련 코드:
- `PointEventProducer`: `send(...).get(3, TimeUnit.SECONDS)`
- `PointController`: 성공 시 `202 Accepted`와 requestId 반환

## 남은 운영 과제

- 회원별 rate limit 정책 추가
- Kafka lag, DLQ count, publish failure count 메트릭화
- DLQ 재처리 도구와 절차 정의
- publish 실패 응답을 공통 에러 포맷으로 변환하는 `@ControllerAdvice` 추가
- 접수 상태 조회 API 추가 (`PENDING`, `SUCCESS`, `FAILED`)
- 더 강한 유실 방지가 필요하면 outbox 패턴 도입
