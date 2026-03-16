```
com.study.point
├── api
│   ├── point
│   │   ├── PointController.java
│   │   ├── request
│   │   │   └── EarnPointRequest.java
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
│           └── PointPolicyPort.java    ← 정책 조회 인터페이스
│
├── domain
│   ├── point
│   │   ├── entity
│   │   │   ├── PointWallet.java        ← Aggregate Root
│   │   │   ├── PointLedger.java
│   │   │   ├── PointUsageDetail.java
│   │   │   └── PointPolicy.java
│   │   ├── vo
│   │   │   └── PointBalance.java       ← Value Object
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
    │   └── PointPolicyCacheAdapter.java
    └── kafka
        └── PointEventProducer.java