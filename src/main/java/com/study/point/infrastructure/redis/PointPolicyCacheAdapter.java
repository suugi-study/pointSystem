package com.study.point.infrastructure.redis;

import com.study.point.application.port.out.PointPolicyPort;
import com.study.point.domain.point.entity.PointPolicy;
import org.springframework.stereotype.Component;

@Component
public class PointPolicyCacheAdapter implements PointPolicyPort {

    @Override
    public PointPolicy loadPolicyFor(Long memberId) {
        // TODO: replace with Redis-backed lookup
        return PointPolicy.defaultPolicy();
    }
}
