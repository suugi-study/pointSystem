package com.study.point.api.point;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.point.api.point.request.EarnPointRequest;
import com.study.point.api.point.request.UsePointRequest;
import com.study.point.api.point.response.PointResponse;
import com.study.point.application.point.PointEarnUseCase;
import com.study.point.application.point.PointUseUseCase;
import com.study.point.application.point.command.EarnPointCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PointController.class)
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PointEarnUseCase pointEarnUseCase;

    @MockBean
    private PointUseUseCase pointUseUseCase;

    @Nested
    @DisplayName("POST /api/points/earn")
    class Earn {

        @Test
        @DisplayName("유효한 요청이면 202 Accepted와 requestId를 반환한다")
        void earn_success() throws Exception {
            // given
            EarnPointRequest request = new EarnPointRequest("1", 10_000L, 30, false, "req-123", "ORDER");

            // when & then
            mockMvc.perform(
                            post("/api/points/earn")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.requestId").value("req-123"));
        }

        @Test
        @DisplayName("requestId가 비어 있어도 컨트롤러가 멱등 키를 생성해 유스케이스로 전달한다")
        void earn_generatesRequestId() throws Exception {
            EarnPointRequest request = new EarnPointRequest("2", 5_000L, 10, true, null, "ADMIN_GRANT");

            mockMvc.perform(
                            post("/api/points/earn")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isAccepted());

            // then
            ArgumentCaptor<EarnPointCommand> captor = ArgumentCaptor.forClass(EarnPointCommand.class);
            verify(pointEarnUseCase).earn(captor.capture());
            EarnPointCommand command = captor.getValue();
            assertThat(command.requestId()).isNotNull();
        }

        @Test
        @DisplayName("검증 오류가 있으면 400 Bad Request 를 반환한다")
        void earn_validationFail() throws Exception {
            // amount = 0 -> @Min(1) 위반
            EarnPointRequest invalid = new EarnPointRequest("1", 0L, 10, false, "req", "ORDER");

            mockMvc.perform(
                            post("/api/points/earn")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(invalid))
                    )
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/points/use")
    class Use {
        @Test
        @DisplayName("유효한 요청이면 200 OK와 ApiResponse.success=true 를 반환한다")
        void use_success() throws Exception {
            UsePointRequest request = new UsePointRequest("3", 7_000L, 99L);
            PointResponse response = new PointResponse(303L, "3", 13_000L, LocalDateTime.of(2024, 6, 1, 12, 0));
            when(pointUseUseCase.use(request.memberId(), request.amount(), request.orderId())).thenReturn(response);

            mockMvc.perform(
                            post("/api/points/use")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.memberId").value("3"))
                    .andExpect(jsonPath("$.data.balance").value(13_000));
        }

        @Test
        @DisplayName("필수값 누락 시 400 Bad Request 를 반환한다")
        void use_validationFail() throws Exception {
            // orderId 누락 -> @NotNull 위반
            String invalidJson = """
                    {
                      "memberId": 3,
                      "amount": 1000
                    }
                    """;

            mockMvc.perform(
                            post("/api/points/use")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(invalidJson)
                    )
                    .andExpect(status().isBadRequest());
        }
    }
}
