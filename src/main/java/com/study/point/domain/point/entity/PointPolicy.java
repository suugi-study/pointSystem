package com.study.point.domain.point.entity;

public record PointPolicy(long maxHoldAmount, long maxEarnPerTransaction, int maxExpireDays) {
    public static PointPolicy defaultPolicy() {
        return new PointPolicy(500_000L, 100_000L, 1825);
    }
}
