package com.study.point.api.point.response;

import java.time.LocalDateTime;

public record PointResponse(
        Long walletId,
        String memberId,
        long balance,
        LocalDateTime updatedAt
) {
}
