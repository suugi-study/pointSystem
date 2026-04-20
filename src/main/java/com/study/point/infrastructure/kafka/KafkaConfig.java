package com.study.point.infrastructure.kafka;

import com.study.point.application.point.command.EarnPointCommand;
import com.study.point.domain.point.exception.PointMaxHoldExceededException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 설정
 *
 * [토픽 구조]
 * - point-earn       : 적립 명령 토픽 (파티션 3개)
 * - point-earn-dlq   : 적립 실패 메시지가 적재되는 Dead Letter Queue
 *
 * [파티셔닝 전략]
 * - Producer key = memberId
 * - 동일 memberId → 항상 동일 파티션 → Consumer에서 순차 처리
 *
 * [컨슈머 그룹 구조]
 * - 그룹: point-wallet
 * - 컨슈머 스레드: 3개 (concurrency = 파티션 수)
 * - 전체는 병렬 처리, 같은 memberId는 순차 처리
 *
 * [신뢰성 보장 전략]
 * - 수동 오프셋 커밋(MANUAL_IMMEDIATE): 처리 완료 후에만 커밋 → 메시지 유실 방지
 * - DLQ + DefaultErrorHandler       : 3회 재시도 후 DLQ로 격리 → 무한 루프 방지
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /** 파티션 수 = 컨슈머 스레드 수 (항상 동일하게 유지) */
    private static final int PARTITION_COUNT = 3;

    /** DLQ 토픽명: 원본 토픽명 + "-dlq" 컨벤션 */
    private static final String DLQ_TOPIC = "point-earn-dlq";

    // =========================================================================
    // 토픽 설정
    // =========================================================================

    /**
     * point-earn 토픽 자동 생성
     * - 파티션 3개: 컨슈머 3개가 각각 1개씩 담당
     */
    @Bean
    public NewTopic pointEarnTopic() {
        return TopicBuilder.name("point-earn")
                .partitions(PARTITION_COUNT)
                .replicas(1)
                .build();
    }

    /**
     * [신규] point-earn-dlq 토픽 자동 생성
     *
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * [개념] DLQ (Dead Letter Queue)
     *   - 처리 불가능한 메시지를 격리 보관하는 별도 토픽
     *   - 원본 토픽 처리를 막지 않기 위해 실패 메시지만 별도로 분리
     *   - 운영자가 나중에 원인을 분석하고 재처리할 수 있게 함
     *
     * [왜 필요한가?]
     *   - 재시도해도 절대 성공 못 하는 메시지(예: 데이터 오류, 제약 위반)가 있을 때
     *     계속 재시도하면 → 해당 파티션이 막혀 뒤 메시지가 밀림(Head-of-Line Blocking)
     *   - DLQ로 격리하면 뒤의 정상 메시지는 계속 처리됨
     *
     * [파티션 수 = 원본과 동일]
     *   - 원본 파티션 번호를 유지해 디버깅/추적성 확보
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     */
    @Bean
    public NewTopic pointEarnDlqTopic() {
        return TopicBuilder.name(DLQ_TOPIC)
                .partitions(PARTITION_COUNT)
                .replicas(1)
                .build();
    }

    // =========================================================================
    // Consumer 설정
    // =========================================================================

    /**
     * EarnPointCommand 역직렬화용 ConsumerFactory
     * - Producer가 type header 없이 발행하므로 VALUE_DEFAULT_TYPE으로 대상 클래스를 명시한다.
     */
    @Bean
    public ConsumerFactory<String, EarnPointCommand> earnConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "point-wallet");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.study.point.application.point.command");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, EarnPointCommand.class.getName());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // [수정] 오토 커밋 비활성화
        //   기본값(true)은 백그라운드 스레드가 주기적으로 오프셋을 자동 커밋함.
        //   → DB 저장 전에 커밋되면, Consumer가 죽었을 때 해당 메시지가 영원히 유실됨.
        //   false로 두고 아래 AckMode.MANUAL_IMMEDIATE로 처리 완료 후 수동 커밋.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(EarnPointCommand.class, false)
        );
    }

    /**
     * 적립 전용 리스너 컨테이너 팩토리
     *
     * [수정 내역]
     *   1. AckMode.MANUAL_IMMEDIATE : 수동 오프셋 커밋
     *   2. DefaultErrorHandler       : 재시도 + DLQ 라우팅
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EarnPointCommand> earnListenerContainerFactory(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, EarnPointCommand> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(earnConsumerFactory());
        factory.setConcurrency(PARTITION_COUNT);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // [신규] 수동 오프셋 커밋 (MANUAL_IMMEDIATE)
        //
        // [개념] 오프셋(offset)이란?
        //   - 각 파티션에서 "내가 어디까지 읽었는지"를 나타내는 포지션 번호
        //   - 커밋 = 해당 offset을 Kafka 브로커에 저장하여 재시작해도 이어서 읽을 수 있게 함
        //
        // [AckMode 비교]
        //   BATCH (기본값)      : poll()로 가져온 메시지 배치 전체를 처리 후 커밋
        //   RECORD              : 메시지 하나 처리 후 즉시 커밋 (수동 아님)
        //   MANUAL              : @KafkaListener 메서드에서 Acknowledgment.ack() 호출 필요, 커밋은 다음 poll 때
        //   MANUAL_IMMEDIATE    : ack() 호출 시점에 '즉시' 커밋 전송 (본 프로젝트 선택)
        //
        // [왜 MANUAL_IMMEDIATE?]
        //   - DB 저장 완료 후에만 ack() 호출 → at-least-once 보장
        //   - 즉시 커밋으로 재시작 시 중복 처리량 최소화
        //   - 중복 자체는 requestId UNIQUE 제약으로 최종 차단되므로 문제없음
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // [신규] 에러 핸들러 + DLQ 라우팅
        //
        // [동작 방식]
        //   1. Consumer가 예외 throw
        //   2. DefaultErrorHandler가 감지 → FixedBackOff 정책대로 재시도
        //      (1초 간격 × 3회)
        //   3. 3회 모두 실패 시 DeadLetterPublishingRecoverer가
        //      해당 메시지를 'point-earn-dlq' 토픽으로 발행
        //   4. 원본 offset은 정상 커밋 처리 → 다음 메시지로 진행
        //
        // [FixedBackOff vs ExponentialBackOff]
        //   - FixedBackOff  : 일정 간격으로 재시도 (단순, 예측 가능)
        //   - Exponential  : 지수적으로 간격 증가 (외부 API 호출 등에 적합)
        //   - 내부 DB 작업이라 FixedBackOff로 충분
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                // 실패 메시지를 DLQ의 동일 파티션 번호로 라우팅 (추적성 확보)
                (record, ex) -> new TopicPartition(DLQ_TOPIC, record.partition())
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(1000L, 3L) // interval=1초, maxAttempts=3회
        );
        // 6. DLQ와 순서 문제:
        //    한 회원의 100건 중 37번째가 비즈니스 규칙(한도 초과 등)으로 실패하면 재시도해도 성공하지 않는다.
        //    이런 예외는 즉시 DLQ로 보내 partition 정체를 줄이고, 시스템 장애성 예외만 재시도한다.
        //    단, DLQ로 빠진 메시지는 원래 순서대로 자동 복원되지 않으므로 운영 재처리 절차가 필요하다.
        errorHandler.addNotRetryableExceptions(PointMaxHoldExceededException.class, IllegalArgumentException.class);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
