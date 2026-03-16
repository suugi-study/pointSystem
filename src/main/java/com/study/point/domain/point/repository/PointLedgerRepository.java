package com.study.point.domain.point.repository;

import com.study.point.domain.point.entity.PointLedger;

public interface PointLedgerRepository {
    PointLedger save(PointLedger ledger);
}
