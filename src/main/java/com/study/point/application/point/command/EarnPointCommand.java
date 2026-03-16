package com.study.point.application.point.command;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EarnPointCommand(
        Long memberId,
        long amount,
        LocalDateTime earnedAt,
        LocalDate expireAt,
        boolean manual
) {
}
