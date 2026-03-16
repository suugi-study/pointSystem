package com.study.point.domain.point.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "point_usage_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUsageDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ledgerId;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private long usedAmount;

    public PointUsageDetail(Long ledgerId, String orderId, long usedAmount) {
        this.ledgerId = ledgerId;
        this.orderId = orderId;
        this.usedAmount = usedAmount;
    }
}
