package com.study.point.application.port.out;

public record PointPolicyConfig(long maxEarnPerOnce, long maxHoldFreePoint) {
    public static PointPolicyConfig defaults() {
        return new PointPolicyConfig(100_000L, 1_000_000L);
    }
}
