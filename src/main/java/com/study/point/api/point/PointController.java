package com.study.point.api.point;

import com.study.point.api.common.ApiResponse;
import com.study.point.api.point.request.EarnPointRequest;
import com.study.point.api.point.request.UsePointRequest;
import com.study.point.api.point.response.PointResponse;
import com.study.point.application.point.PointEarnUseCase;
import com.study.point.application.point.PointUseUseCase;
import com.study.point.application.point.command.EarnPointCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 포인트 적립/사용 Controller
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/points")
public class PointController {

    /** 포인트 적립 */
    private final PointEarnUseCase pointEarnUseCase;
    /** 포인트 사용(차감) */
    private final PointUseUseCase pointUseUseCase;

    /**
     * 포인트 적립
     * - 요청을 검증 후 Kafka point-earn 토픽에 발행하고 202 Accepted를 반환한다.
     * - 실제 적립 처리는 Consumer가 비동기로 수행한다.
     * - requestId 미지정 시 서버에서 UUID 생성하여 멱등성 확보
     */
    @PostMapping("/earn")
    public ResponseEntity<ApiResponse<Map<String, String>>> earn(@Valid @RequestBody EarnPointRequest request) {
        String requestId = request.requestId() != null ? request.requestId() : UUID.randomUUID().toString();
        EarnPointCommand command = new EarnPointCommand(
                request.memberId(),
                request.amount(),
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(request.expireInDays()),
                request.manual(),
                request.pointType(), // sourceType: 기본값(관리자 지급). 주문/이벤트 시 확장 가능.
                null,          // sourceId: 원천 식별자(주문번호 등). 현재는 생략.
                requestId
        );
        pointEarnUseCase.earn(command);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(Map.of("requestId", requestId)));
    }

    /**
     * 포인트 사용
     * - README 요구사항 “어떤 주문에서 사용되었는지 추적”을 위해 주문 ID를 입력값으로 받는다.
     * - 현재 구현은 지갑 차감까지만 처리하며, 주문별 사용 상세 기록(PointUsageDetail)은 도메인 확장 시 연결 가능.
     */
    @PostMapping("/use")
    public ResponseEntity<ApiResponse<PointResponse>> use(@Valid @RequestBody UsePointRequest request) {
        // memberId: 지갑 소유자, amount: 차감 금액, orderId: 어느 주문에서 사용했는지 추적용
        PointResponse response = pointUseUseCase.use(request.memberId(), request.amount(), request.orderId());
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok(response));
    }
}
