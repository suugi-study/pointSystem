```
com.study.point
├── api
│   ├── point
│   │   ├── PointController.java
│   │   ├── request
│   │   │   ├── EarnPointRequest.java
│   │   │   └── UsePointRequest.java
│   │   └── response
│   │       └── PointResponse.java
│   └── common
│       └── ApiResponse.java            ← 공통 응답 래퍼
│
├── application
│   ├── point
│   │   ├── PointEarnUseCase.java       ← 포인트 적립 유스케이스
│   │   ├── PointUseUseCase.java        ← 포인트 사용 유스케이스
│   │   └── command
│   │       └── EarnPointCommand.java
│   └── port
│       └── out
│           ├── PointPolicyPort.java    ← 정책 조회 포트
│           └── PointPolicyConfig.java  ← 정책 기본값 레코드
│
├── domain
│   ├── point
│   │   ├── entity
│   │   │   ├── PointWallet.java        ← Aggregate Root
│   │   │   ├── PointLedger.java
│   │   │   ├── PointUsageDetail.java
│   │   │   ├── PointPolicy.java
│   │   │   ├── PointLedgerEarnType.java
│   │   │   └── PointLedgerStatus.java
│   │   ├── vo
│   │   │   ├── PointBalance.java       ← Value Object
│   │   │   └── EarnPolicy.java
│   │   ├── repository
│   │   │   ├── PointWalletRepository.java
│   │   │   └── PointLedgerRepository.java
│   │   └── exception
│   │       ├── PointMaxHoldExceededException.java
│   │       └── InsufficientPointException.java
│
└── infrastructure
    ├── persistence
    │   ├── PointWalletJpaRepository.java
    │   └── PointLedgerJpaRepository.java
    ├── redis
    │   ├── PointPolicyCacheAdapter.java
    │   └── RedisLockManager.java       ← 분산락/동시성 제어
    └── kafka
        ├── KafkaConfig.java            ← Kafka 설정
        └── PointEventProducer.java
```

## Architecture / Patterns 사용 개요
- **Layered + Hexagonal(Ports & Adapters)**: `api`(입구) → `application`(유스케이스, 트랜잭션 경계) → `domain`(엔티티/VO/도메인 규칙) → `infrastructure`(JPA/Redis/Kafka 구현). 외부 시스템 의존성은 포트(`application.port`)로 추상화.
- **DDD 용어 적용**: `PointWallet`을 Aggregate Root, `PointBalance`를 Value Object로 사용. 도메인 예외로 규칙 위반을 표현.
- **정책 외부화**: 포인트 한도·만료 정책을 DB 테이블(`point_policy`)로 두고 `PointPolicyPort`와 Redis 캐시 어댑터로 로드, 하드코딩 회피.
- **멱등성 처리**: 적립 요청의 `requestId`를 원장 UNIQUE 키로 사용해 중복 적립 방지.
- **동시성 제어**: `PointWallet.version`에 JPA Optimistic Lock 사용. 원장 상태는 `remaining`/`status`로 관리.
- **검증 & 추적성**: Jakarta Bean Validation으로 API 입력 제약, `@Comment`/DDL 코멘트로 스키마 문서화, `created_at/updated_at`은 Spring Data Auditing.
- **만료/소진 모델링**: 원장 단위(`PointLedger`)로 만료일(`expire_at`)과 상태(`ACTIVE/EXHAUSTED/EXPIRED`)를 관리해 주문별 사용 추적을 가능하게 설계.
- **이벤트 발행**: 적립 시 `PointEventProducer`로 Kafka 이벤트 발행(후속 처리 확장 포인트).
