# 한 회원이 거의 동시에 100개의 적립 요청을 쏘는 경우, Kafka를 잘 쓰면 “처리 순서”는 꽤 안정적으로 만들 수 있습니다. 다만 Kafka가 모든 문제를 해결해주지는 않고, API 진입부, producer, consumer, DB 각각
  에서 다른 문제가 생길 수 있습니다.

## 전제
  memberId를 Kafka message key로 보내고, 같은 topic 안에서 같은 key가 항상 같은 partition으로 가도록 구성한다면, 같은 회원의 적립 이벤트 100개는 같은 partition에 들어갑니다. Kafka는 한 partition 안에서
  는 offset 순서를 보장하므로 consumer도 기본적으로 순서대로 읽을 수 있습니다.

  발생 가능한 문제

  1. 중복 적립

  사용자가 버튼을 여러 번 누르거나, 클라이언트/서버/producer 재시도 때문에 같은 적립 요청이 여러 번 Kafka에 들어갈 수 있습니다.

  예를 들어 실제 의도는 requestId=req-1 한 건인데, 네트워크 타임아웃 때문에 producer가 재전송하면 consumer 입장에서는 같은 요청을 두 번 받을 수 있습니다.

  대응:

  - 적립 요청마다 requestId 같은 멱등성 키 필요
  - point_ledger.request_id에 unique 제약 필요
  - consumer에서 이미 처리된 requestId면 skip
  - Kafka producer enable.idempotence=true는 도움은 되지만 비즈니스 중복까지 완전히 막지는 못함

  2. 회원별 순서는 보장되지만 전체 처리량은 막힐 수 있음

  같은 memberId는 같은 partition으로 몰립니다. 이건 순서 보장에는 좋지만, 한 회원이 100개 요청을 몰아서 보내면 해당 partition의 consumer thread 하나가 그 회원 요청을 순차 처리해야 합니다.

  문제는 같은 partition에 들어간 다른 회원 요청도 뒤에 줄을 서게 될 수 있다는 점입니다. Kafka의 순서 보장은 partition 단위라서, 한 partition이 뜨거워지면 해당 partition 전체가 병목이 됩니다.

  대응:

  - key를 memberId로 두면 회원별 순서는 강하지만 hot key 문제가 생김
  - partition 수를 충분히 확보
  - 특정 회원 트래픽이 과도하면 rate limit 또는 API 레벨 throttling 필요
  - “회원별 순서”가 절대 요구사항이면 한 회원의 적립은 병렬 처리하지 않는 게 맞음

  3. DB 잔액 갱신 경쟁

  Kafka consumer가 같은 회원 요청을 순서대로 읽더라도, 구조에 따라 DB 경쟁이 생길 수 있습니다.

  예를 들어 consumer concurrency가 3이고 같은 회원이 같은 partition에만 들어간다면 보통 하나의 consumer thread가 순서대로 처리합니다. 이 경우 같은 회원의 100건은 순차 처리되어 잔액 경쟁이 크게 줄어듭니
  다.

  하지만 아래 상황이면 문제가 생길 수 있습니다.

  - key가 memberId가 아니라 랜덤 값이다
  - topic이 여러 개로 나뉘어 같은 회원 이벤트가 분산된다
  - consumer 내부에서 비동기 처리로 DB 저장을 병렬화한다
  - 수동 ack 전에 별도 thread pool으로 넘긴다
  - 적립 API 일부는 Kafka를 거치고, 일부는 DB에 직접 쓴다

  그럼 같은 지갑 row를 동시에 읽고 갱신하면서 lost update가 날 수 있습니다.

  대응:

  - 같은 회원 적립은 반드시 같은 key로 publish
  - consumer에서 메시지 처리 완료 전 ack 금지
  - consumer 내부에서 같은 회원 이벤트를 병렬 DB 처리하지 않기
  - PointWallet에 @Version 낙관적 락 또는 PESSIMISTIC_WRITE 락 유지
  - 원장 insert + 지갑 balance update를 하나의 transaction으로 처리

  4. 최대 보유 한도 검증 오류

  요구사항에 “개인별 보유 가능한 무료포인트 최대금액 제한”이 있습니다.

  예를 들어 현재 잔액이 90,000이고, 1,000 포인트 적립 요청 100개가 들어오면 총 100,000이 추가됩니다. 최대 보유 한도가 100,000이라면 앞의 10건만 성공하고 나머지는 실패해야 합니다.

  문제는 병렬 처리되면 여러 consumer가 동시에 현재 잔액 90,000을 보고 각각 성공 판단을 해버릴 수 있다는 점입니다.

  Kafka memberId key 기반 순차 처리라면 이 문제는 줄어듭니다. 그래도 DB transaction 경계가 중요합니다.

  대응:

  - 적립 처리 순서: 지갑 조회 → 정책 검증 → 원장 저장 → 지갑 잔액 증가 → commit
  - 이 전체가 하나의 transaction이어야 함
  - 지갑 row lock 또는 optimistic lock 필요
  - 한도 초과 건은 DLQ로 보낼지, 실패 원장으로 저장할지 정책 필요

  5. 원장과 지갑 잔액 불일치

  적립 시스템에서는 보통 두 가지 데이터를 같이 관리합니다.

  - point_ledger: 적립 원장
  - point_wallet: 현재 잔액 집계

  100건 처리 중간에 장애가 나면 다음 문제가 생길 수 있습니다.

  - 원장은 insert 됐는데 wallet balance update 실패
  - wallet balance는 증가했는데 원장 저장 실패
  - DB commit은 됐는데 Kafka ack 전에 consumer 죽음
  - Kafka ack는 했는데 DB commit 전에 consumer 죽음

  대응:

  - DB 작업은 하나의 transaction
  - Kafka ack는 DB commit 이후
  - ack 전에 죽어서 재처리되는 경우는 requestId unique로 멱등 처리
  - 원장을 source of truth로 보고 wallet은 집계 캐시처럼 복구 가능하게 설계하는 게 안전함

  6. DLQ가 순서를 깨뜨릴 수 있음

  100개 중 37번째 메시지가 계속 실패한다고 가정하면 선택지가 있습니다.

  - 37번째가 성공할 때까지 뒤의 38~100번을 막는다
  - 37번째를 DLQ로 보내고 38번부터 계속 처리한다

  현재 구조처럼 retry 후 DLQ로 보내면 전체 처리는 계속 진행됩니다. 하지만 “적립 요청 순서를 반드시 의미적으로 보장해야 한다”면 37번째 실패 후 38번째를 처리하는 것이 도메인적으로 맞는지 봐야 합니다.

  예를 들어 37번째가 한도 초과라 실패하고 38번째도 같은 한도 문제라면 괜찮을 수 있습니다. 하지만 37번째가 정책 조회 일시 장애로 실패한 것이라면 뒤 요청을 먼저 처리하는 것이 이상할 수 있습니다.

  대응:

  - 검증 실패와 시스템 실패를 구분
  - 비즈니스 실패는 실패 원장으로 남기고 다음 메시지 진행 가능
  - 시스템 실패는 retry를 더 강하게 하거나 partition 처리를 멈추는 전략 고려
  - DLQ 재처리 시 원래 순서 복원은 어렵다는 점 인지

  7. API 서버에서 Kafka publish 전 폭주

  Kafka consumer가 순차 처리하더라도 API 서버는 100개의 요청을 거의 동시에 받습니다. 이때 발생 가능한 문제는 다음입니다.

  - 같은 requestId 없이 100개가 모두 정상 요청으로 들어감
  - producer send buffer가 밀림
  - Kafka broker 장애 시 API 응답 정책이 불명확
  - 사용자는 성공 응답을 받았는데 실제 적립은 나중에 실패

  대응:

  - API는 “적립 완료”가 아니라 “적립 요청 접수”로 응답해야 함
  - 202 Accepted가 적절
  - requestId 반환
  - 상태 조회 API 필요: PENDING / SUCCESS / FAILED
  - Kafka publish 실패 시에는 접수 실패로 응답하거나 outbox 패턴 사용

  현재 구조에서 특히 중요한 포인트

  지금 방향은 맞습니다.

  - Producer key를 memberId로 둔다
  - Consumer에서 DB 저장 후 ack 한다
  - requestId unique로 중복을 막는다
  - 지갑 row는 pessimistic lock 또는 optimistic lock으로 보호한다

  다만 “한 회원의 100개 적립”에서 가장 중요한 건 이겁니다.

  Kafka 순서 보장 + DB 멱등성 + 트랜잭션 + 회원별 잔액 락

  Kafka만 있으면 순서는 보장되지만, 중복/재처리/DB 불일치/한도 검증 문제는 여전히 남습니다. 그래서 최종 방어선은 DB unique constraint와 transaction이어야 합니다.

 
› * 매번 새 객체 생성
