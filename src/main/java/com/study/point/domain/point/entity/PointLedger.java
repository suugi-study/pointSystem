package com.study.point.domain.point.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 포인트 원장(point_ledger) 엔티티.
 * ddl.sql 요구사항:
 * - 적립 건(1원 단위)을 개별 행으로 기록하여 “어느 주문/이벤트/관리자 지급인지”와 만료일(expire_at)을 추적.
 * - remaining 컬럼으로 사용 후 잔여 금액을 관리하고, earn_type(SYSTEM/MANUAL)으로 수기 지급 식별.
 * - wallet_id FK 로 지갑과 연결하여 회원 단위 잔액 집계와 정합성을 유지한다.
 * - requestId UNIQUE 로 중복 적립을 막고, status(ACTIVE/EXHAUSTED/EXPIRED)로 만료/소진 상태를 명확히 표현한다.
 */
@Entity
@Table(
        name = "point_ledger",
        indexes = {
                @jakarta.persistence.Index(name = "idx_ledger_wallet_expire", columnList = "wallet_id, expire_at, status"),
                @jakarta.persistence.Index(name = "idx_ledger_source", columnList = "source_type, source_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ledger_wallet"))
    private PointWallet wallet;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long remaining;

    @Enumerated(EnumType.STRING)
    @Column(name = "earn_type", nullable = false, length = 20)
    private PointLedgerEarnType earnType;

    /**
     * 멱등성을 보장하기 위한 요청 식별자 (동일 요청 중복 적립 방지).
     * entity_refactoring.txt #2 참고: UNIQUE 제약 필요.
     */
    @Column(name = "request_id", length = 100, unique = true, updatable = false)
    private String requestId;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "expire_at", nullable = false)
    private LocalDateTime expireAt;

    /**
     * 만료 여부 대신 상태값으로 관리 (ACTIVE/EXHAUSTED/EXPIRED).
     * entity_refactoring.txt #1 권고 반영.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PointLedgerStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private PointLedger(PointWallet wallet, long amount, long remaining, PointLedgerEarnType earnType,
                        String sourceType, Long sourceId, LocalDateTime expireAt, String requestId) {
        this.wallet = wallet;
        this.amount = amount;
        this.remaining = remaining;
        this.earnType = earnType;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.expireAt = expireAt;
        this.status = PointLedgerStatus.ACTIVE;
        this.requestId = requestId;
    }

    public static PointLedger earn(PointWallet wallet, long amount, PointLedgerEarnType earnType,
                                   String sourceType, Long sourceId, LocalDateTime expireAt, String requestId) {
        return new PointLedger(wallet, amount, amount, earnType, sourceType, sourceId, expireAt, requestId);
    }

    public PointUsageDetail use(long useAmount, Long orderId) {
        if (useAmount > remaining) {
            throw new IllegalArgumentException("Use amount exceeds remaining balance");
        }
        this.remaining -= useAmount;
        if (this.remaining == 0) {
            this.status = PointLedgerStatus.EXHAUSTED;
        }
        return PointUsageDetail.of(this, orderId, useAmount);
    }

    public void markExpired() {
        this.status = PointLedgerStatus.EXPIRED;
    }
}
