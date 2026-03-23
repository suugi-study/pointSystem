package com.study.point.application.point.command;

import java.time.LocalDateTime;

public record EarnPointCommand(
        Long memberId,
        long amount,
        LocalDateTime earnedAt,
        LocalDateTime expireAt,
        boolean manual,
        String sourceType,
        Long sourceId,
        String requestId
) {
}
