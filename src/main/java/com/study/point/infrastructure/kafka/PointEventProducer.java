package com.study.point.infrastructure.kafka;

import com.study.point.application.point.command.EarnPointCommand;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PointEventProducer {

    private static final String TOPIC_EARN = "point-earn";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PointEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 적립 명령을 Kafka point-earn 토픽에 발행한다.
     * key = memberId: 같은 회원의 요청이 동일 파티션으로 라우팅되어 Consumer에서 순차 처리된다.
     */
    public void sendEarnCommand(EarnPointCommand command) {
        kafkaTemplate.send(TOPIC_EARN, String.valueOf(command.memberId()), command);
    }
}
