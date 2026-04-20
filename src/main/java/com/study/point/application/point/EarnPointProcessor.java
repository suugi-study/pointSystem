package com.study.point.application.point;

import com.study.point.application.point.command.EarnPointCommand;
import com.study.point.application.port.out.PointPolicyConfig;
import com.study.point.application.port.out.PointPolicyPort;
import com.study.point.domain.point.entity.PointLedger;
import com.study.point.domain.point.entity.PointLedgerEarnType;
import com.study.point.domain.point.entity.PointWallet;
import com.study.point.domain.point.repository.PointLedgerRepository;
import com.study.point.domain.point.repository.PointWalletRepository;
import com.study.point.domain.point.vo.EarnPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka Consumer로부터 위임받아 실제 적립을 처리한다.
 * - requestId로 중복 여부 확인 후 이미 처리된 경우 무시한다.
 * - PointWallet, PointLedger를 트랜잭션 안에서 반영한다.
 * - 최종 정합성은 DB 트랜잭션 + requestId UNIQUE 제약으로 보장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EarnPointProcessor {

    private final PointWalletRepository pointWalletRepository;
    private final PointLedgerRepository pointLedgerRepository;
    private final PointPolicyPort pointPolicyPort;

    public void process(EarnPointCommand command) {
        // 1. 중복 적립 방어: 같은 requestId가 재전달되면 이미 생성된 원장을 기준으로 멱등 처리한다.
        //    Kafka producer/consumer 재시도는 정상 상황에서도 중복 전달을 만들 수 있으므로 DB UNIQUE가 최종 방어선이다.
        if (pointLedgerRepository.findByRequestId(command.requestId()).isPresent()) {
            log.info("Duplicate earn request ignored: requestId={}", command.requestId());
            return;
        }

        PointPolicyConfig policy = pointPolicyPort.loadPolicy();

        // 3. DB 잔액 갱신 경쟁 방어: repository의 PESSIMISTIC_WRITE 락으로 회원 지갑 row를 잠근다.
        //    Kafka key=memberId가 깨지거나 다른 경로에서 같은 지갑을 수정해도 DB가 마지막 방어선이 된다.
        PointWallet wallet = pointWalletRepository.findByMemberId(command.memberId())
                .orElseGet(() -> pointWalletRepository.save(PointWallet.create(command.memberId())));

        EarnPolicy earnPolicy = EarnPolicy.of(policy.maxEarnPerOnce(), policy.maxHoldFreePoint());
        // 4. 최대 보유 한도 검증: 100건이 순서대로 들어와도 매 건 최신 잔액 기준으로 한도를 다시 판단한다.
        wallet.earn(command.amount(), earnPolicy);

        // 5. 원장과 지갑 잔액 정합성: 원장 insert와 지갑 잔액 update는 이 메서드의 @Transactional 경계 안에서 함께 커밋된다.
        //    커밋 전 장애는 둘 다 rollback, 커밋 후 ack 전 장애는 requestId 멱등 처리로 중복 반영을 막는다.
        PointLedger ledger = PointLedger.earn(
                wallet,
                command.amount(),
                command.manual() ? PointLedgerEarnType.MANUAL : PointLedgerEarnType.SYSTEM,
                command.pointType(),
                command.sourceId(),
                command.expireAt(),
                command.requestId()
        );

        pointLedgerRepository.save(ledger);
        pointWalletRepository.save(wallet);

        log.info("Earn processed: requestId={}, memberId={}, amount={}",
                command.requestId(), command.memberId(), command.amount());
    }
}
