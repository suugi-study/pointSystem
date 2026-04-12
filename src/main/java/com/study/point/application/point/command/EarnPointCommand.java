package com.study.point.application.point.command;

import java.time.LocalDateTime;

/**
 *
 * 포인트 적립” 유스케이스에 전달되는 입력 값을 한데 모은 명령 DTO
 *
 * @param  memberId: 적립 대상 회원 ID.
 *     @param  amount: 적립 금액(원 단위).
 *     @param  earnedAt: 적립 시각(보통 요청 시각).
 *     @param  expireAt: 만료 시각(정책 또는 요청으로 결정).
 *     @param  manual: 관리자 수기 지급 여부(true면 MANUAL, 아니면 SYSTEM).
 *     @param  sourceType: 적립 원천 구분(예: ORDER, ADMIN_GRANT, EVENT).
 *     @param  sourceId: 원천 식별자(주문번호 등), 필요 없으면 null.
 *     @param requestId: 멱등성 키; 동일 요청 중복 적립을 막기 위해 UNIQUE로 사용.
 */

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
