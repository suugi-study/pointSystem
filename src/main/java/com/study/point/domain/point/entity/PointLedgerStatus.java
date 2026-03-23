package com.study.point.domain.point.entity;

/**
 * 원장 상태 관리용 enum.
 * ACTIVE: 유효, EXHAUSTED: 잔여 0, EXPIRED: 만료 처리됨 (배치 처리 여부와 구분).
 */
public enum PointLedgerStatus {
    ACTIVE,
    EXHAUSTED,
    EXPIRED
}
