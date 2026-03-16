package com.study.point.application.port.out;

import com.study.point.domain.point.entity.PointPolicy;

public interface PointPolicyPort {
    PointPolicy loadPolicyFor(Long memberId);
}
