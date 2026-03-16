package com.study.point.domain.point.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 포인트 정책 테이블(point_policy)과 1:1 매핑되는 엔티티.
 * - 하드코딩 없이 '1회 최대 적립(MAX_EARN_PER_ONCE)', '최대 보유(MAX_HOLD_FREE_POINT)'와 같은 정책을 저장/변경한다.
 * - policy_key 는 유니크하며, policy_value 에 실제 한도 값을 저장한다.
 * - created_at / updated_at 은 정책 변경 이력을 남기기 위해 사용한다.
 */
@Entity
@Table(name = "point_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id")
    private Long id;

    @Column(name = "policy_key", nullable = false, unique = true, length = 100)
    private String key;

    @Column(name = "policy_value", nullable = false)
    private long value;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private PointPolicy(String key, long value, String description) {
        this.key = key;
        this.value = value;
        this.description = description;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static PointPolicy of(String key, long value, String description) {
        return new PointPolicy(key, value, description);
    }

    public void updateValue(long newValue) {
        this.value = newValue;
        this.updatedAt = LocalDateTime.now();
    }
}
