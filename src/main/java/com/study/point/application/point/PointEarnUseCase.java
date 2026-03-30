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
import com.study.point.infrastructure.kafka.PointEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class PointEarnUseCase {

    private final PointWalletRepository pointWalletRepository;
    private final PointLedgerRepository pointLedgerRepository;
    private final PointPolicyPort pointPolicyPort;
    private final PointEventProducer pointEventProducer;

    public PointResponse earn(EarnPointCommand command) {
        PointPolicyConfig policy = pointPolicyPort.loadPolicy();

        Optional<PointLedger> existing = pointLedgerRepository.findByRequestId(command.requestId());
        if (existing.isPresent()) {
            PointWallet wallet = existing.get().getWallet();
            return new PointResponse(wallet.getId(), wallet.getMemberId(), wallet.getFreeBalance(), wallet.getUpdatedAt());
        }

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
        pointEventProducer.publishEarned(ledger);

        return new PointResponse(wallet.getId(), wallet.getMemberId(), wallet.getBalance().getAvailable(), wallet.getUpdatedAt());
    }
}
