package com.study.point.domain.point.vo;

/**
 * 포인트 적립 정책 값을 캡슐화한 VO.
 * - maxEarnPerOnce: 1회 최대 적립 가능 금액
 * - maxHoldFreePoint: 지갑 최대 보유 가능 금액
 */
public record EarnPolicy(long maxEarnPerOnce, long maxHoldFreePoint) {
    public EarnPolicy {
        if (maxEarnPerOnce <= 0) {
            throw new IllegalArgumentException("maxEarnPerOnce must be positive");
        }
        if (maxHoldFreePoint <= 0) {
            throw new IllegalArgumentException("maxHoldFreePoint must be positive");
        }
        if (maxEarnPerOnce > maxHoldFreePoint) {
            throw new IllegalArgumentException("maxEarnPerOnce must not exceed maxHoldFreePoint");
        }
    }

    public static EarnPolicy of(long maxEarnPerOnce, long maxHoldFreePoint) {
        return new EarnPolicy(maxEarnPerOnce, maxHoldFreePoint);
    }
}
