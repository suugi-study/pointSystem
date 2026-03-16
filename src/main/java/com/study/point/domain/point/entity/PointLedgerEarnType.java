package com.study.point.domain.point.entity;

/**
 * 적립 유형 구분.
 * ddl.sql 의 earn_type 체크 제약(SYSTEM/MANUAL)과 매핑하여,
 * 자동 적립(SYSTEM)과 관리자 수기지급(MANUAL)을 명확히 구분한다.
 */
public enum PointLedgerEarnType {
    SYSTEM,
    MANUAL
}
