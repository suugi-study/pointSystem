package com.study.point.domain.point.entity;

import com.study.point.domain.point.exception.InsufficientPointException;
import com.study.point.domain.point.exception.PointMaxHoldExceededException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 포인트 지갑(point_wallet) 엔티티.
 * README 요구사항 중 “회원별 보유 포인트 최대 금액”, “적립/사용 집계”를 담당한다.
 * - member_id 별로 1:1 유니크하게 존재하며 free_balance 는 현재 사용 가능한 무료포인트를 나타낸다.
 * - total_earned / total_used 로 누적 집계를 관리해 감사 추적을 용이하게 한다.
 * - version 컬럼은 낙관적 락(@Version)으로 동시성 상황에서 잔액 오차를 방지한다.
 * - created_at / updated_at 은 적립·사용 시각을 기록한다.
 */
@Entity
@Table(
        name = "point_wallet",
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(name = "uq_wallet_member", columnNames = "member_id"),
        indexes = @jakarta.persistence.Index(name = "idx_wallet_member_id", columnList = "member_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_id")
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(name = "free_balance", nullable = false)
    private long freeBalance;

    @Column(name = "total_earned", nullable = false)
    private long totalEarned;

    @Column(name = "total_used", nullable = false)
    private long totalUsed;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private PointWallet(Long memberId) {
        this.memberId = memberId;
        this.freeBalance = 0L;
        this.totalEarned = 0L;
        this.totalUsed = 0L;
        this.version = 0L;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static PointWallet create(Long memberId) {
        return new PointWallet(memberId);
    }

    public void earn(long amount, long maxEarnPerOnce, long maxHoldFreePoint) {
        if (amount > maxEarnPerOnce) {
            throw new PointMaxHoldExceededException("Exceeded per-transaction earn limit");
        }
        if (freeBalance + amount > maxHoldFreePoint) {
            throw new PointMaxHoldExceededException("Wallet hold limit exceeded");
        }
        this.freeBalance += amount;
        this.totalEarned += amount;
        touch();
    }

    public void use(long amount) {
        if (amount > freeBalance) {
            throw new InsufficientPointException("Insufficient points");
        }
        this.freeBalance -= amount;
        this.totalUsed += amount;
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
