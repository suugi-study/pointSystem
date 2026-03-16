package com.study.point.infrastructure.redis;

import com.study.point.application.port.out.PointPolicyConfig;
import com.study.point.application.port.out.PointPolicyPort;
import org.springframework.stereotype.Component;

@Component
public class PointPolicyCacheAdapter implements PointPolicyPort {

    @Override
    public PointPolicyConfig loadPolicy() {
        // TODO: replace with Redis-backed lookup and policy table integration
        return PointPolicyConfig.defaults();
    }
}
