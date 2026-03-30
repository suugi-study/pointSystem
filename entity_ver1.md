전체적으로 완성도가 꽤 높은 설계입니다. 업로드된 엔티티들을 꼼꼼히 분석해서 강점, 개선 포인트, 그리고 수정 코드까지 같이 드릴게요.

---

## ✅ 강점 (잘 설계된 부분)

**1. 3계층 동시성 방어선 구축**
`PointWallet`의 `@Version` + 향후 Redisson 분산락 + DB CHECK 제약 조합 설계 의도가 엔티티에 잘 반영되어 있습니다.

**2. 멱등성 설계**
`PointLedger.requestId`에 `unique = true, updatable = false`를 걸어서 중복 적립을 DB 레벨에서도 방지한 것은 매우 좋습니다.

**3. 도메인 로직의 엔티티 캡슐화**
`PointWallet.earn()`, `use()` 내부에서 정책 검증 후 상태 변경 → Rich Domain Model 방향을 잘 따르고 있습니다.

**4. 상태 기반 원장 관리**
`PointLedgerStatus (ACTIVE/EXHAUSTED/EXPIRED)` 로 boolean 대신 상태값 관리 — 배치 만료 처리와 자연 소진을 명확히 구분한 것이 인상적입니다.

---

## ⚠️ 개선이 필요한 부분

### 문제 1. `PointWallet` - `@PrePersist`/`@PreUpdate`와 생성자 내 시각 이중 설정

**현재 코드의 문제:**
생성자에서도 `LocalDateTime.now()`를 세팅하고, `@PrePersist`에서도 또 세팅합니다. `null` 체크로 막고 있지만, `touch()` 메서드와 `@PreUpdate`가 동시에 `updatedAt`을 건드려서 **의미가 중복**됩니다.

```java
// 현재: touch()와 @PreUpdate 둘 다 updatedAt 갱신 → 혼란
private void touch() {
    this.updatedAt = LocalDateTime.now(); // ← 이미 여기서 갱신
}

@PreUpdate
void onUpdate() {
    updatedAt = LocalDateTime.now(); // ← 중복
}
```

**권장 방향:** `@PrePersist`/`@PreUpdate`에만 위임하거나 `touch()`만 사용하되 `@PreUpdate`는 제거. `Spring Data Auditing`으로 일원화하는 게 가장 깔끔합니다.

```java
// 권장: Spring Data Auditing으로 일원화
@EntityListeners(AuditingEntityListener.class)
public class PointWallet {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // touch() 메서드 및 @PrePersist, @PreUpdate 전부 제거
}

// Application 메인 또는 Config에 활성화
@EnableJpaAuditing
```

---

### 문제 2. `PointLedger` - `@PrePersist`/생성자 이중 세팅 + `createdAt` 생성자 주입 누락

```java
// 생성자에서 이미 세팅
this.createdAt = LocalDateTime.now();

// @PrePersist에서 null 체크 후 또 세팅 → 생성자가 항상 먼저 실행되므로 @PrePersist 블록은 사실상 dead code
@PrePersist
void onCreate() {
    if (createdAt == null) { // ← 항상 false
        createdAt = LocalDateTime.now();
    }
    updatedAt = createdAt; // ← 이건 실행됨 (updatedAt 초기화 목적이라면 생성자에 넣어야 일관성)
}
```

---

### 문제 3. `PointUsageDetail` - `@PrePersist` 없고 생성자 `public` 접근 제어 누락

다른 엔티티는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`인데, `PointUsageDetail`의 패키지 전용 생성자가 `public`으로 열려 있어 **외부에서 직접 생성 가능**합니다.

```java
// 현재 (문제)
public PointUsageDetail(PointLedger ledger, Long orderId, long usedAmount) { ... }

// 권장: 정적 팩토리 메서드 패턴 통일
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUsageDetail {

    public static PointUsageDetail of(PointLedger ledger, Long orderId, long usedAmount) {
        PointUsageDetail detail = new PointUsageDetail();
        detail.ledger = ledger;
        detail.orderId = orderId;
        detail.usedAmount = usedAmount;
        detail.usedAt = LocalDateTime.now();
        return detail;
    }
}
```

---

### 문제 4. `PointWallet.earn()` - 정책 값을 파라미터로 받는 구조의 레이어 오염 위험

```java
// 현재: Application 레이어에서 정책 값을 꺼내 도메인으로 주입
public void earn(long amount, long maxEarnPerOnce, long maxHoldFreePoint) { ... }
```

이 자체가 나쁜 건 아니지만, 호출부(Application Service)에서 매번 `PointPolicy`를 조회해서 넘겨야 하므로 **실수 가능성**이 있습니다. `EarnPolicy` 값 객체로 감싸는 방법을 권장합니다.

```java
// 권장: 정책을 값 객체로 캡슐화
public record EarnPolicy(long maxEarnPerOnce, long maxHoldFreePoint) {
    public static EarnPolicy from(Map<String, PointPolicy> policies) {
        return new EarnPolicy(
            policies.get("MAX_EARN_PER_ONCE").getValue(),
            policies.get("MAX_HOLD_FREE_POINT").getValue()
        );
    }
}

// 도메인 메서드 시그니처
public void earn(long amount, EarnPolicy policy) {
    if (amount > policy.maxEarnPerOnce()) { ... }
    if (freeBalance + amount > policy.maxHoldFreePoint()) { ... }
    ...
}
```

---

### 문제 5. `PointLedger.use()` - `PointUsageDetail` 생성 책임 분리

현재 `PointLedger.use()`는 `remaining`만 줄이고, `PointUsageDetail` 생성은 외부(Application)에서 따로 해야 합니다. 이 두 동작이 **원자적으로 묶여야** 하는 도메인 규칙인데 엔티티가 강제하지 않아서 Application 레이어에서 실수할 여지가 있습니다.

```java
// 권장: use()가 PointUsageDetail을 반환하여 원자성 보장
public PointUsageDetail use(long useAmount, Long orderId) {
    if (useAmount > remaining) {
        throw new IllegalArgumentException("Use amount exceeds remaining balance");
    }
    this.remaining -= useAmount;
    this.updatedAt = LocalDateTime.now();
    if (this.remaining == 0) {
        this.status = PointLedgerStatus.EXHAUSTED;
    }
    return PointUsageDetail.of(this, orderId, useAmount); // 원자적으로 같이 생성
}
```

---

### 문제 6. `PointPolicy` - `effectiveFrom` 유효성 검증 없음

정책이 미래 시점에 활성화되는 개념인데, 현재 `enable` 여부와 `effectiveFrom` 검증이 **쿼리 레벨에서도, 도메인 레벨에서도 없습니다.**

```java
// 권장: 도메인 메서드에 유효성 검증 추가
public boolean isActive() {
    return this.enabled && !LocalDateTime.now().isBefore(this.effectiveFrom);
}

public long getActiveValue() {
    if (!isActive()) {
        throw new IllegalStateException("Policy [" + key + "] is not active yet or disabled");
    }
    return this.value;
}
```

---

## 📋 전체 평가 요약

| 항목 | 평가 | 비고 |
|------|------|------|
| 동시성 방어 (@Version) | ⭐⭐⭐⭐⭐ | 완벽 |
| 멱등성 (requestId unique) | ⭐⭐⭐⭐⭐ | 완벽 |
| 상태 기반 설계 | ⭐⭐⭐⭐⭐ | 완벽 |
| Rich Domain Model | ⭐⭐⭐⭐☆ | use() 원자성 보완 필요 |
| Auditing 일관성 | ⭐⭐⭐☆☆ | PrePersist 중복 정리 필요 |
| 정적 팩토리 패턴 일관성 | ⭐⭐⭐☆☆ | PointUsageDetail 수정 필요 |
| 정책 캡슐화 | ⭐⭐⭐☆☆ | EarnPolicy VO 도입 권장 |

---

## 📚 참고 출처

- Spring Data JPA Auditing: https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#auditing
- DDD Aggregate Root 패턴: Eric Evans, *Domain-Driven Design* (2003), Ch. 6
- JPA `@Version` Optimistic Lock: Jakarta Persistence 3.1 Spec §3.4.2
- Effective Java 3rd Ed. Item 1: "정적 팩토리 메서드를 생성자 대신 고려하라"
