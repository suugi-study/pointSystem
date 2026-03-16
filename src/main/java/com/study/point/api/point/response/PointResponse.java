package com.study.point.api.point.response;

import java.time.LocalDateTime;

public record PointResponse(
        Long walletId,
        Long memberId,
        long balance,
        LocalDateTime updatedAt
) {
}
