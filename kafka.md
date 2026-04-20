## 적립(EARN) 처리 방향

### 적립은 Redis 락을 주 처리 방식으로 사용하지 않는다.
적립 요청은 Kafka에 먼저 넣고, 같은 사용자 기준으로 줄 세워 순차 처리한다.

### 적립 처리 흐름
1. 클라이언트가 적립 요청 전송
2. API 서버가 요청 검증
3. `requestId`가 없으면 서버에서 생성
4. Kafka `point-earn` 토픽에 적립 명령 발행
5. Kafka Consumer가 메시지를 읽음
6. Consumer가 DB에서 `requestId` 중복 여부 확인
7. 중복이 아니면 `PointWallet`, `PointLedger`, `PointRequest` 반영
8. 트랜잭션 commit

### 적립 설계 의도
- API 서버를 가볍게 유지
- 순간 트래픽 급증을 Kafka가 흡수
- 같은 사용자 요청을 순차 처리
- Redis 락 의존도를 낮춤

---

## 사용(USE) 처리 방향

사용은 적립과 다르게 실시간 차감 정합성이 중요하다.  
따라서 사용 시에는 Redis 락을 걸고 처리하는 구조를 사용한다.

### 사용 처리 흐름
1. 클라이언트가 사용 요청 전송
2. Redis 락 획득 (`lock:point:use:{memberId}`)
3. DB에서 현재 잔액 조회
4. 차감 가능 여부 검증
5. `PointWallet`, `PointLedger`, `PointUsageDetail` 반영
6. 트랜잭션 commit
7. Redis 락 해제

### 사용 설계 의도
- 같은 사용자에 대한 동시 차감 충돌 방지
- 차감 시점의 잔액 정합성 확보
- 실시간 응답 구조 유지

### Redis 락 주의사항
- 단순 `DEL` 해제가 아니라, 내가 획득한 락인지 token 비교 후 해제해야 한다.
- Redis 락은 보조 장치이며, 최종 정합성은 DB 트랜잭션이 보장해야 한다.

---

## Kafka 설계 원칙

## Kafka에서의 개념
- **토픽(topic)**: 이벤트 종류별 큰 분류
- **파티션(partition)**: 토픽 안의 병렬 처리 단위
- **메시지 key**: 어떤 파티션으로 갈지 결정하는 값
- **메시지 value**: 실제 비즈니스 데이터

### 중요한 개념
Kafka는 **토픽 내부에 여러 개의 파티션이 존재**한다.  
순서는 **파티션 내부에서만 보장**된다.

즉, 같은 사용자 요청을 같은 파티션으로 보내야 순차 처리할 수 있다.

---

## 적립용 Kafka 토픽/파티션 전략

### 토픽
- `point-earn`

### 파티션
- 처음에는 6개 정도로 시작
- 추후 처리량/consumer 수에 따라 조정 가능

### Kafka 메시지 key
- `memberId` 또는 `walletId`

### Kafka 메시지 value
- `requestId`
- `memberId`
- `orderId`
- `amount`
- `createdAt`

### 왜 requestId를 Kafka key로 사용하지 않는가
`requestId`는 요청마다 달라지므로, 같은 사용자의 요청이 여러 파티션으로 흩어질 수 있다.  
그러면 같은 사용자 요청의 순차 처리가 깨진다.

따라서:

- **중복제어 기준** = `requestId`
- **순차처리 기준** = `memberId` 또는 `walletId`

이 둘은 역할이 다르므로 분리해야 한다.

---

## Kafka Consumer의 역할

Kafka Consumer는 `point-earn` 토픽에 들어온 적립 요청 메시지를 읽어서 실제 적립 로직을 실행시키는 시작점이다.

### Consumer 역할
1. Kafka 토픽에서 메시지 수신
2. 적립 UseCase 호출
3. 비즈니스 로직은 서비스 계층으로 위임

### Consumer가 직접 해야 하는 일
- 메시지 수신
- 서비스/유스케이스 호출

### Consumer가 직접 하지 말아야 할 일
- 모든 비즈니스 로직을 Consumer 클래스 안에 몰아넣는 것
- 복잡한 도메인 규칙을 리스너 메서드에 직접 작성하는 것

### 권장 구조
- Consumer: 메시지 수신 및 위임
- Application UseCase: 적립 흐름 제어
- Domain/Repository: 잔액/원장 반영

---

## 중복제어 전략

Kafka는 비동기 처리와 순차 처리에는 도움을 주지만,  
비즈니스 중복제어 자체를 자동으로 해주지는 않는다.

따라서 중복 적립 방지는 아래 조합으로 처리한다.

### 중복제어 기준
- `requestId`

### 중복제어 방법
1. API 요청 시 `requestId` 확인 또는 생성
2. Consumer 처리 시 DB에서 `requestId` 존재 여부 조회
3. `request_id` 컬럼에 unique 제약조건 부여
4. 이미 처리된 `requestId`면 무시

### 핵심 테이블
- `point_request` 또는 `point_earn_request`
- `request_id unique`

### 정리
- Kafka는 순서를 보장
- DB는 중복 처리를 최종 차단
- Redis는 필요하면 1차 중복 체크나 짧은 상태 저장에 활용 가능

---

## 추천 테이블 역할

### PointWallet
- 현재 회원 포인트 잔액

### PointLedger
- 적립/사용 원장
- 어떤 요청으로 얼마가 적립/차감되었는지 기록

### PointRequest
- `requestId` 기준 멱등 처리 상태 저장
- 중복 요청 방지용

### PointUsageDetail
- 어떤 적립분이 어떤 사용 요청에 사용되었는지 추적

---

## 테스트 원칙

### Controller 테스트
- `@WebMvcTest`
- 의존 유스케이스는 `@MockBean`

### JPA 테스트
- `@DataJpaTest`

### 전체 통합 테스트
- `@SpringBootTest`
- 외부 인프라 의존성은 최소화
- `application-test.yml` 분리 고려

### 테스트 시 주의사항
- 컨트롤러 테스트에서 전체 컨텍스트를 올리지 않는다.
- Kafka/Redis/Oracle이 꼭 필요한 테스트만 통합 테스트로 둔다.
- 적립 중복, 같은 사용자 순차 처리, 차감 락 동작을 테스트 포인트로 둔다.

---

## 구현 시 꼭 지켜야 할 사항

1. 적립 요청은 Kafka를 통해 비동기 순차 처리한다.
2. 적립에서 Kafka key는 `memberId` 또는 `walletId`를 사용한다.
3. `requestId`는 Kafka key가 아니라 중복제어용 값이다.
4. 적립 중복제어는 DB unique 제약으로 최종 보장한다.
5. 사용은 Redis 락을 이용해 동시 차감을 제어한다.
6. Redis 락 해제는 token 비교 후 안전하게 수행한다.
7. 최종 포인트 정합성은 반드시 Oracle DB 트랜잭션으로 보장한다.
8. Consumer는 메시지 수신과 위임 역할 중심으로 유지한다.
9. 도메인 로직은 UseCase/Service 계층에 둔다.
10. 코드 생성 시 보안(SQL Injection), 성능(N+1), 트랜잭션 경계, 테스트 가능성을 항상 고려한다.