package com.study.point.api.point.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 포인트 사용 요청 DTO
 * - 주문별 사용 추적을 위해 orderId를 함께 받는다.
 * @param memberId 포인트를 보유한 회원 ID
 * @param amount   차감할 포인트 금액(1 이상)
 * @param orderId  어느 주문에서 사용했는지 추적하기 위한 주문 ID
 */
public record UsePointRequest(
        @NotNull Long memberId,
        @Min(1) long amount,
        @NotNull Long orderId
) {
}
