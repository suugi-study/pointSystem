package com.study.point.api.point.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 포인트 적립 요청 DTO
 * @param memberId    적립 대상 회원 ID
 * @param amount      적립 금액(1~100,000)
 * @param expireInDays 만료까지 남은 일수(1~1825)
 * @param manual      관리자가 수기 지급한 경우 true
 * @param requestId   멱등성 키(없으면 서버가 UUID 생성)
 */
public record EarnPointRequest(
        @NotNull Long memberId,
        @Min(1) @Max(100_000) long amount,
        @Min(1) @Max(1825) int expireInDays,
        boolean manual,
        @Size(max = 100) String requestId
) {
}
