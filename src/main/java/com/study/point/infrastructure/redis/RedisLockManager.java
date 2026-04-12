package com.study.point.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis 분산 락 유틸.
 * - 높은 TPS(예: 1000) 상황에서 동일 회원 지갑을 동시에 적립/차감하면 중복 갱신·더블스펜드가 발생할 수 있다.
 * - memberId 기반 키로 선점(setIfAbsent)하여 같은 회원의 동시 요청을 직렬화한다.
 * - 짧은 TTL을 설정해 장애 시 락이 영구 고착되지 않도록 방지하고, 해제 시 토큰을 검증해 오동작을 막는다.
 */
@Component
@RequiredArgsConstructor
public class RedisLockManager {

    private final StringRedisTemplate stringRedisTemplate;

    /** 락 획득: 성공 시 토큰 반환, 실패 시 null */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        if (Boolean.TRUE.equals(locked)) {
            return token;
        }
        return null;
    }

    /** 락 해제: 토큰이 일치할 때만 삭제하여 타 요청의 락을 지우지 않도록 보호 */
    public void unlock(String key, String token) {
        try {
            String current = stringRedisTemplate.opsForValue().get(key);
            if (token != null && token.equals(current)) {
                stringRedisTemplate.delete(key);
            }
        } catch (DataAccessException ignore) {
            // Redis 장애 시에도 비즈니스 로직이 진행될 수 있도록 무시하지만,
            // 모니터링에 경고가 잡히도록 로깅/메트릭 연동을 권장.
        }
    }
}
