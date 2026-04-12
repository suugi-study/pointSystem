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
        // 지갑 단위 순차 처리:
        //  - message key를 walletId로 고정하면 Kafka 파티셔너가 같은 key를 동일 파티션에 배치한다.
        //  - 동일 지갑 건은 Consumer에서 사실상 단일 writer처럼 순차로 처리되어 동시성 충돌이 줄어든다.
        kafkaTemplate.send(TOPIC_EARNED, String.valueOf(ledger.getWallet().getId()), ledger);
    }
}
