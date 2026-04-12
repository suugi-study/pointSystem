package com.study.point.application.point;

import com.study.point.domain.point.entity.PointWallet;
import com.study.point.domain.point.repository.PointWalletRepository;
import com.study.point.infrastructure.redis.RedisLockManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.study.point.api.point.response.PointResponse;
import java.time.Duration;

/**
 * 사용(차감) 유스케이스 서비스 설계 메모
 * - 트랜잭션 경계: 지갑 조회 → 잔액 차감 → 저장을 한 트랜잭션으로 묶어 동시성 시 일관성 확보.
 * - 단순 책임: 금액 검증·차감 로직은 PointWallet 엔티티에 위임하고, 유스케이스는 시나리오 흐름만 담당.
 * - 예외 전략: 지갑 미존재 시 명확한 IllegalArgumentException을 던져 상위 계층에서 처리할 수 있게 함.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PointUseUseCase {

    private final PointWalletRepository pointWalletRepository;
    private final RedisLockManager redisLockManager;

    public PointResponse use(Long memberId, long amount, Long orderId) {
        String lockKey = "lock:wallet:" + memberId;
        // 1단계 게이트(경량): Redis 락으로 단기간 겹침만 필터링 (TPS 1000 시 큐 폭증 방지)
        String token = redisLockManager.tryLock(lockKey, Duration.ofSeconds(5));
        if (token == null) {
            throw new IllegalStateException("Concurrent use request in progress for member " + memberId);
        }
        try {
            PointWallet wallet = pointWalletRepository.findByMemberId(memberId)
                    .orElseThrow(() -> new IllegalArgumentException("Wallet not found for member " + memberId));
            // TODO: 주문별 사용 상세 기록(PointUsageDetail 생성)은 주문/원장 매핑 로직 합류 시 추가
            wallet.use(amount);
            pointWalletRepository.save(wallet);
            return new PointResponse(wallet.getId(), wallet.getMemberId(), wallet.getFreeBalance(), wallet.getUpdatedAt());
        } finally {
            redisLockManager.unlock(lockKey, token);
        }
    }
}
