package com.study.point.application.point;

import com.study.point.api.point.response.PointResponse;
import com.study.point.application.point.command.EarnPointCommand;
import com.study.point.application.port.out.PointPolicyPort;
import com.study.point.domain.point.entity.PointLedger;
import com.study.point.domain.point.entity.PointPolicy;
import com.study.point.domain.point.entity.PointWallet;
import com.study.point.domain.point.repository.PointLedgerRepository;
import com.study.point.domain.point.repository.PointWalletRepository;
import com.study.point.infrastructure.kafka.PointEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PointEarnUseCase {

    private final PointWalletRepository pointWalletRepository;
    private final PointLedgerRepository pointLedgerRepository;
    private final PointPolicyPort pointPolicyPort;
    private final PointEventProducer pointEventProducer;

    public PointResponse earn(EarnPointCommand command) {
        PointPolicy policy = pointPolicyPort.loadPolicyFor(command.memberId());

        PointWallet wallet = pointWalletRepository.findByMemberId(command.memberId())
                .orElseGet(() -> pointWalletRepository.save(PointWallet.create(command.memberId())));

        wallet.earn(command.amount(), policy);
        PointLedger ledger = PointLedger.earn(wallet, command.amount(), command.earnedAt(), command.expireAt(), command.manual());

        pointLedgerRepository.save(ledger);
        pointWalletRepository.save(wallet);
        pointEventProducer.publishEarned(ledger);

        return new PointResponse(wallet.getId(), wallet.getMemberId(), wallet.getBalance().getAvailable(), wallet.getUpdatedAt());
    }
}
