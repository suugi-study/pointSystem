package com.study.point.application.point;

import com.study.point.domain.point.entity.PointWallet;
import com.study.point.domain.point.repository.PointWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PointUseUseCase {

    private final PointWalletRepository pointWalletRepository;

    public PointWallet use(Long memberId, long amount) {
        PointWallet wallet = pointWalletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for member " + memberId));
        wallet.use(amount);
        return pointWalletRepository.save(wallet);
    }
}
