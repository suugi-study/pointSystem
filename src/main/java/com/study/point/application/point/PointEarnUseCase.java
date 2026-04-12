package com.study.point.application.point;

import com.study.point.api.point.response.PointResponse;
import com.study.point.application.point.command.EarnPointCommand;
import com.study.point.application.port.out.PointPolicyConfig;
import com.study.point.application.port.out.PointPolicyPort;
import com.study.point.domain.point.entity.PointLedger;
import com.study.point.domain.point.entity.PointLedgerEarnType;
import com.study.point.domain.point.entity.PointWallet;
import com.study.point.domain.point.repository.PointLedgerRepository;
import com.study.point.domain.point.repository.PointWalletRepository;
import com.study.point.domain.point.vo.EarnPolicy;
import com.study.point.infrastructure.redis.RedisLockManager;
import com.study.point.infrastructure.kafka.PointEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.time.Duration;

/**
 * 적립 유스케이스 서비스 설계 메모
 * - 트랜잭션 경계: 정책 조회 → 지갑 로드/생성 → 원장 생성·저장 → 이벤트 발행까지 한 번에 처리하여 정합성 보장.
 * - 정책 하드코딩 방지: PointPolicyPort로 DB 정책을 읽어 EarnPolicy로 변환 후 검증을 도메인(지갑)에게 위임.
 * - 멱등성: requestId로 기존 원장을 먼저 조회해 중복 적립을 차단하고, 동일 요청은 기존 지갑 상태를 그대로 반환.
 * - 역할 분리: 잔액 검증·증감과 상태 변경은 엔티티(PointWallet, PointLedger)에 맡기고, 이 클래스는 시나리오 조립과 이벤트 발행만 담당.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PointEarnUseCase {

    private final PointWalletRepository pointWalletRepository;
    private final PointLedgerRepository pointLedgerRepository;
    private final PointPolicyPort pointPolicyPort;
    private final PointEventProducer pointEventProducer;
    private final RedisLockManager redisLockManager;

    public PointResponse earn(EarnPointCommand command) {
        String lockKey = "lock:wallet:" + command.memberId();
        // 1단계 게이트(경량): Redis 분산 락
        //  - 목적: 동일 wallet/member 요청이 같은 순간 몰릴 때 단기간 겹침만 필터링 (5초 TTL)
        //  - Kafka로 흘려보내기 전 단계에서 중복 요청을 즉시 차단해 TPS 1000에서도 지연이 폭증하지 않도록 함
        String token = redisLockManager.tryLock(lockKey, Duration.ofSeconds(5));
        if (token == null) {
            throw new IllegalStateException("Concurrent earn request in progress for member " + command.memberId());
        }

        try {
            PointPolicyConfig policy = pointPolicyPort.loadPolicy();

            // 2단계 멱등성: requestId UNIQUE 조회로 완전 중복 적립을 제거
            Optional<PointLedger> existing = pointLedgerRepository.findByRequestId(command.requestId());
            if (existing.isPresent()) {
                PointWallet wallet = existing.get().getWallet();
                return new PointResponse(wallet.getId(), wallet.getMemberId(), wallet.getFreeBalance(), wallet.getUpdatedAt());
            }

            // 3단계 직렬화: JPA PESSIMISTIC_WRITE (@Lock)로 동일 지갑을 DB 단에서 하나씩 처리
            PointWallet wallet = pointWalletRepository.findByMemberId(command.memberId())
                    .orElseGet(() -> pointWalletRepository.save(PointWallet.create(command.memberId())));

            EarnPolicy earnPolicy = EarnPolicy.of(policy.maxEarnPerOnce(), policy.maxHoldFreePoint());
            wallet.earn(command.amount(), earnPolicy);
            PointLedger ledger = PointLedger.earn(
                    wallet,
                    command.amount(),
                    command.manual() ? PointLedgerEarnType.MANUAL : PointLedgerEarnType.SYSTEM,
                    command.sourceType(),
                    command.sourceId(),
                    command.expireAt(),
                    command.requestId()
            );

            pointLedgerRepository.save(ledger);
            pointWalletRepository.save(wallet);

            // 4단계 파이프라인: Kafka key = walletId 로 파티셔닝 → Consumer에서 동일 지갑을 단일 파티션(=단일 writer)로 처리
            //    idempotent producer + key 파티셔닝으로, 동일 wallet의 순서를 보장해 병목을 줄이고 정합성을 높인다.
            pointEventProducer.publishEarned(ledger);

            // README 요구사항: 적립 후 현재 무료 포인트 잔액을 응답한다.
            return new PointResponse(wallet.getId(), wallet.getMemberId(), wallet.getFreeBalance(), wallet.getUpdatedAt());
        } finally {
            redisLockManager.unlock(lockKey, token);
        }
    }
}
