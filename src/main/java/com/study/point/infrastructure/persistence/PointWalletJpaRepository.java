package com.study.point.infrastructure.persistence;

import com.study.point.domain.point.entity.PointWallet;
import com.study.point.domain.point.repository.PointWalletRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PointWalletJpaRepository extends JpaRepository<PointWallet, Long>, PointWalletRepository {
    @Override
    Optional<PointWallet> findByMemberId(Long memberId);
}
