package com.study.point.domain.point.entity;

import com.study.point.domain.point.exception.InsufficientPointException;
import com.study.point.domain.point.exception.PointMaxHoldExceededException;
import com.study.point.domain.point.vo.EarnPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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
        uniqueConstraints = @UniqueConstraint(name = "uq_wallet_member", columnNames = "member_id"),
        indexes = @jakarta.persistence.Index(name = "idx_wallet_member_id", columnList = "member_id")
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Comment("회원별 포인트 지갑 (잔액 집계)")
public class

PointWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_id")
    @Comment("포인트 지갑 PK")
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true)
    @Comment("회원 ID (1:1 고유 지갑)")
    private String memberId;

    @Column(name = "free_balance", nullable = false)
    @Comment("현재 사용 가능한 무료 포인트 잔액")
    private long freeBalance;

    @Column(name = "total_earned", nullable = false)
    @Comment("누적 적립 총액")
    private long totalEarned;

    @Column(name = "total_used", nullable = false)
    @Comment("누적 사용 총액")
    private long totalUsed;

    @Version
    @Column(name = "version", nullable = false)
    @Comment("JPA @Version - Optimistic Lock용")
    private long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("지갑 생성 시각")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    @Comment("지갑 최종 수정 시각")
    private LocalDateTime updatedAt;

    private PointWallet(String memberId) {
        this.memberId = memberId;
        this.freeBalance = 0L;
        this.totalEarned = 0L;
        this.totalUsed = 0L;
        this.version = 0L;
    }

    public static PointWallet create(String memberId) {
        return new PointWallet(memberId);
    }

    public void earn(long amount, EarnPolicy policy) {
        if (amount > policy.maxEarnPerOnce()) {
            throw new PointMaxHoldExceededException("Exceeded per-transaction earn limit");
        }
        if (freeBalance + amount > policy.maxHoldFreePoint()) {
            throw new PointMaxHoldExceededException("Wallet hold limit exceeded");
        }
        this.freeBalance += amount;
        this.totalEarned += amount;
    }

    public void use(long amount) {
        if (amount > freeBalance) {
            throw new InsufficientPointException("Insufficient points");
        }
        this.freeBalance -= amount;
        this.totalUsed += amount;
    }
}
