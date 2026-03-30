package com.study.point.domain.point.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 포인트 정책 테이블(point_policy)과 1:1 매핑되는 엔티티.
 * - 하드코딩 없이 '1회 최대 적립(MAX_EARN_PER_ONCE)', '최대 보유(MAX_HOLD_FREE_POINT)'와 같은 정책을 저장/변경한다.
 * - policy_key 는 유니크하며, policy_value 에 실제 한도 값을 저장한다.
 * - dataType/unit/effectiveFrom/enabled 로 타입·단위·유효기간을 명확히 관리해 운영 변경을 안전하게 한다.
 * - created_at / updated_at 은 정책 변경 이력을 남기기 위해 사용한다.
 */
@Entity
@Table(name = "point_policy")
@EntityListeners(AuditingEntityListener.class)
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

    @Column(name = "data_type", length = 30, nullable = false)
    private String dataType; // e.g. NUMBER, STRING

    @Column(name = "unit", length = 30)
    private String unit; // e.g. POINT, DAY

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private PointPolicy(String key, long value, String description,
                        String dataType, String unit, boolean enabled,
                        LocalDateTime effectiveFrom, String updatedBy) {
        this.key = key;
        this.value = value;
        this.description = description;
        this.dataType = dataType;
        this.unit = unit;
        this.enabled = enabled;
        this.effectiveFrom = effectiveFrom;
        this.updatedBy = updatedBy;
    }

    public static PointPolicy of(String key, long value, String description,
                                 String dataType, String unit,
                                 boolean enabled, LocalDateTime effectiveFrom, String updatedBy) {
        return new PointPolicy(key, value, description, dataType, unit, enabled, effectiveFrom, updatedBy);
    }

    public void updateValue(long newValue, boolean enabled, LocalDateTime effectiveFrom, String updatedBy) {
        this.value = newValue;
        this.enabled = enabled;
        this.effectiveFrom = effectiveFrom;
        this.updatedBy = updatedBy;
    }

    public boolean isActive() {
        return this.enabled && !LocalDateTime.now().isBefore(this.effectiveFrom);
    }

    public long getActiveValue() {
        if (!isActive()) {
            throw new IllegalStateException("Policy [" + key + "] is not active yet or disabled");
        }
        return this.value;
    }
}
