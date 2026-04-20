package com.study.point.infrastructure.kafka;

import com.study.point.application.point.EarnPointProcessor;
import com.study.point.application.point.command.EarnPointCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
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

    @KafkaListener(
            topics = "point-earn",
            groupId = "point-wallet",
            containerFactory = "earnListenerContainerFactory"
    )
    public void consume(EarnPointCommand command, Acknowledgment ack) {
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // [수정] Acknowledgment 파라미터 추가 + 수동 ack 호출
        //
        // [개념] Acknowledgment
        //   - Spring Kafka가 제공하는 수동 커밋용 객체
        //   - @KafkaListener 메서드 파라미터로 선언하면 주입됨
        //   - ack() 호출 시점에 해당 메시지의 offset이 Kafka로 커밋됨
        //
        // [왜 DB 저장 후에 ack()?]
        //   - DB 저장 전에 ack() → 저장 중 크래시 시 메시지 유실
        //   - DB 저장 후에 ack() → 크래시 시 재시작 후 재처리 (중복은 requestId로 방지)
        //   → "at-least-once 처리" 보장
        //
        // [예외가 발생하면?]
        //   - ack()가 호출되지 않고 예외가 밖으로 전파됨
        //   - KafkaConfig의 DefaultErrorHandler가 감지 → 재시도 → DLQ 라우팅
        //   - try/catch로 여기서 예외를 삼키면 안 됨 (삼키면 DLQ로 가지 않음)
        //
        // [순서 보장 범위]
        //   - 같은 memberId는 producer key로 같은 partition에 들어오므로 이 listener가 offset 순서대로 처리한다.
        //   - 여기서 별도 thread pool으로 넘겨 병렬 DB 저장을 하면 회원별 순서 보장이 깨질 수 있으므로 직접 처리한다.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        log.info("Received earn command: requestId={}, memberId={}", command.requestId(), command.memberId());

        earnPointProcessor.process(command);

        // 처리 완료 후에만 커밋 (예외 발생 시 이 줄은 실행되지 않음)
        ack.acknowledge();
    }
}
