package com.study.point.domain.point.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private PointWallet wallet;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private LocalDateTime earnedAt;

    @Column(nullable = false)
    private LocalDate expireAt;

    @Column(nullable = false)
    private boolean manual;

    private PointLedger(PointWallet wallet, long amount, LocalDateTime earnedAt, LocalDate expireAt, boolean manual) {
        this.wallet = wallet;
        this.amount = amount;
        this.earnedAt = earnedAt;
        this.expireAt = expireAt;
        this.manual = manual;
    }

    public static PointLedger earn(PointWallet wallet, long amount, LocalDateTime earnedAt, LocalDate expireAt, boolean manual) {
        return new PointLedger(wallet, amount, earnedAt, expireAt, manual);
    }
}
