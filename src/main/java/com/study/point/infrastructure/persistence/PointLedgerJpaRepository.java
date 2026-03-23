package com.study.point.infrastructure.persistence;

import com.study.point.domain.point.entity.PointLedger;
import com.study.point.domain.point.repository.PointLedgerRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PointLedgerJpaRepository extends JpaRepository<PointLedger, Long>, PointLedgerRepository {
    @Override
    Optional<PointLedger> findByRequestId(String requestId);
}
