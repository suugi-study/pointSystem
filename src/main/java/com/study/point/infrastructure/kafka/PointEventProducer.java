package com.study.point.infrastructure.kafka;

import com.study.point.application.point.command.EarnPointCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class PointEventProducer {

    private static final String TOPIC_EARN = "point-earn";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PointEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 적립 명령을 Kafka point-earn 토픽에 발행한다.
     * - key = memberId: 같은 회원의 요청이 동일 파티션으로 라우팅되어 Consumer에서 순차 처리된다.
     *
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * [수정] 전송 결과 확인 후 HTTP 계층으로 복귀
     *
     * [개념] KafkaTemplate.send()의 반환값
     *   - CompletableFuture<SendResult>를 반환하는 비동기 API
     *   - 실제 브로커 전송은 백그라운드 스레드가 수행
     *   - 호출 스레드는 기다리지 않고 바로 리턴 → HTTP 응답 지연 없음
     *
     * [왜 확인이 필요한가?]
     *   - send() 호출 성공 ≠ 브로커 저장 성공
     *   - 네트워크 문제, 브로커 다운 등으로 실제 발행 실패 가능
     *   - 실패를 감지하지 못하면 '메시지 유실'이 조용히 일어남
     *
     * [선택지 비교]
     *   A. .get(timeout)         : 동기 대기. 브로커 저장 성공을 확인한 뒤 202 반환 (본 프로젝트 선택)
     *   B. whenComplete 콜백     : HTTP 지연은 적지만, 발행 실패 후 이미 202가 나갈 수 있음
     *   C. ProducerListener      : 전역 리스너로 모든 send 결과 감지 (다수 토픽일 때 유리)
     *
     * [운영 고려]
     *   - 실패 시 API는 접수 실패로 응답해야 하며, 운영에서는 메트릭/알람 연동 필요
     *   - Producer 자체에는 acks=all + enable.idempotence=true 설정으로
     *     브로커 측 재시도는 이미 보장됨 (application.yml 참조)
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     */
    public void sendEarnCommand(EarnPointCommand command) {
        String key = String.valueOf(command.memberId());

        try {
            // API는 "Kafka에 접수됨"을 의미하는 202를 반환하므로, 브로커 저장 성공까지 확인한다.
            // timeout을 두어 Kafka 장애가 HTTP worker를 무기한 붙잡지 않도록 한다.
            SendResult<String, Object> result = kafkaTemplate.send(TOPIC_EARN, key, command)
                    .get(3, TimeUnit.SECONDS);

            log.debug("Kafka send OK: topic={}, partition={}, offset={}, requestId={}",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    command.requestId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publish interrupted: requestId=" + command.requestId(), e);
        } catch (ExecutionException | TimeoutException e) {
            log.error("Kafka send FAILED: topic={}, key={}, requestId={}, error={}",
                    TOPIC_EARN, key, command.requestId(), e.getMessage(), e);
            throw new IllegalStateException("Kafka publish failed: requestId=" + command.requestId(), e);
        }
    }
}
