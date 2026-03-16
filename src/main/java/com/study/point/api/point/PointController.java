package com.study.point.api.point;

import com.study.point.api.common.ApiResponse;
import com.study.point.api.point.request.EarnPointRequest;
import com.study.point.api.point.response.PointResponse;
import com.study.point.application.point.PointEarnUseCase;
import com.study.point.application.point.PointUseUseCase;
import com.study.point.application.point.command.EarnPointCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/points")
public class PointController {

    private final PointEarnUseCase pointEarnUseCase;
    private final PointUseUseCase pointUseUseCase;

    @PostMapping("/earn")
    public ApiResponse<PointResponse> earn(@Valid @RequestBody EarnPointRequest request) {
        EarnPointCommand command = new EarnPointCommand(
                request.memberId(),
                request.amount(),
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(request.expireInDays()),
                request.manual(),
                "ADMIN_GRANT",
                null
        );
        PointResponse response = pointEarnUseCase.earn(command);
        return ApiResponse.ok(response);
    }
}
