package com.study.point.domain.point.repository;

import com.study.point.domain.point.entity.PointWallet;

import java.util.Optional;

public interface PointWalletRepository {
    PointWallet save(PointWallet wallet);

    Optional<PointWallet> findByMemberId(Long memberId);
}
