package com.study.point.api.point.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EarnPointRequest(
        @NotNull Long memberId,
        @Min(1) @Max(100_000) long amount,
        @Min(1) @Max(1825) int expireInDays,
        boolean manual,
        @Size(max = 100) String requestId
) {
}
