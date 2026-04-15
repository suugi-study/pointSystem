package com.study.point.application.point;

import com.study.point.application.point.command.EarnPointCommand;
import com.study.point.infrastructure.kafka.PointEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 적립 유스케이스
 * - 요청을 검증하고 Kafka point-earn 토픽에 발행한다.
 * - 실제 적립 처리(중복 체크, DB 저장)는 Consumer → EarnPointProcessor에서 수행된다.
 */
@Service
@RequiredArgsConstructor
public class PointEarnUseCase {

    private final PointEventProducer pointEventProducer;

    public void earn(EarnPointCommand command) {
        pointEventProducer.sendEarnCommand(command);
    }
}
