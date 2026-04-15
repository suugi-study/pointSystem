package com.study.point.infrastructure.kafka;

import com.study.point.application.point.EarnPointProcessor;
import com.study.point.application.point.command.EarnPointCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * point-earn 토픽 Consumer
 * - 메시지 수신 후 EarnPointProcessor로 위임한다.
 * - 비즈니스 로직을 직접 처리하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointEarnKafkaConsumer {

    private final EarnPointProcessor earnPointProcessor;

    @KafkaListener(topics = "point-earn", groupId = "point-wallet")
    public void consume(EarnPointCommand command) {
        log.info("Received earn command: requestId={}, memberId={}", command.requestId(), command.memberId());
        earnPointProcessor.process(command);
    }
}
