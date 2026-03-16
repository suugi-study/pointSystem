package com.study.point.domain.point.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDateTime;

/**
 * 포인트 사용 상세(point_usage_detail) 엔티티.
 * - 특정 원장(ledger_id)에서 어느 주문(order_id)에 얼마를 사용했는지 1원 단위로 기록한다.
 * - README 요구사항 “어떤 주문에서 사용되었는지 추적”을 만족하기 위한 테이블 매핑.
 * - used_at 으로 사용 시점을 남겨 포렌식/정산에 활용한다.
 */
@Entity
@Table(name = "point_usage_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUsageDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "detail_id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ledger_id", nullable = false, foreignKey = @ForeignKey(name = "fk_usage_ledger"))
    private PointLedger ledger;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "used_amount", nullable = false)
    private long usedAmount;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;

    public PointUsageDetail(PointLedger ledger, Long orderId, long usedAmount) {
        this.ledger = ledger;
        this.orderId = orderId;
        this.usedAmount = usedAmount;
        this.usedAt = LocalDateTime.now();
    }
}
