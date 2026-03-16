package com.study.point.domain.point.entity;

import com.study.point.domain.point.exception.PointMaxHoldExceededException;
import com.study.point.domain.point.vo.PointBalance;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "point_wallet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long memberId;

    @Embedded
    private PointBalance balance;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private PointWallet(Long memberId, PointBalance balance, LocalDateTime updatedAt) {
        this.memberId = memberId;
        this.balance = balance;
        this.updatedAt = updatedAt;
    }

    public static PointWallet create(Long memberId) {
        return new PointWallet(memberId, new PointBalance(0L), LocalDateTime.now());
    }

    public void earn(long amount, PointPolicy policy) {
        if (amount > policy.maxEarnPerTransaction()) {
            throw new PointMaxHoldExceededException("Exceeded per-transaction earn limit");
        }
        long nextBalance = balance.getAvailable() + amount;
        if (nextBalance > policy.maxHoldAmount()) {
            throw new PointMaxHoldExceededException("Wallet hold limit exceeded");
        }
        this.balance = balance.add(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public void use(long amount) {
        this.balance = balance.subtract(amount);
        this.updatedAt = LocalDateTime.now();
    }
}
