package com.study.point.infrastructure.persistence;

import com.study.point.domain.point.entity.PointWallet;
import com.study.point.domain.point.repository.PointWalletRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PointWalletJpaRepository extends JpaRepository<PointWallet, Long>, PointWalletRepository {
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE) // 동일 memberId에 대한 동시 적립/차감 충돌을 DB에서 마지막 방어선으로 직렬화
    Optional<PointWallet> findByMemberId(String memberId);
}
