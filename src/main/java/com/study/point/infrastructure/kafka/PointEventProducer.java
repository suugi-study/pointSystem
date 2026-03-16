package com.study.point.infrastructure.kafka;

import com.study.point.domain.point.entity.PointLedger;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PointEventProducer {

    private static final String TOPIC_EARNED = "point-earned";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PointEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishEarned(PointLedger ledger) {
        kafkaTemplate.send(TOPIC_EARNED, String.valueOf(ledger.getId()), ledger);
    }
}
