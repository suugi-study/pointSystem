package com.study.point.domain.point.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EarnPolicyTest {

    @Test
    @DisplayName("유효한 정책값이면 EarnPolicy를 생성한다")
    void of_success() {
        EarnPolicy policy = EarnPolicy.of(100_000L, 1_000_000L);

        assertThat(policy.maxEarnPerOnce()).isEqualTo(100_000L);
        assertThat(policy.maxHoldFreePoint()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("1회 최대 적립 금액은 양수여야 한다")
    void of_fail_whenMaxEarnPerOnceIsNotPositive() {
        assertThatThrownBy(() -> EarnPolicy.of(0L, 1_000_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxEarnPerOnce must be positive");
    }

    @Test
    @DisplayName("최대 보유 가능 금액은 양수여야 한다")
    void of_fail_whenMaxHoldFreePointIsNotPositive() {
        assertThatThrownBy(() -> EarnPolicy.of(100_000L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxHoldFreePoint must be positive");
    }

    @Test
    @DisplayName("1회 최대 적립 금액은 최대 보유 가능 금액보다 클 수 없다")
    void of_fail_whenMaxEarnPerOnceExceedsMaxHoldFreePoint() {
        assertThatThrownBy(() -> EarnPolicy.of(1_000_001L, 1_000_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxEarnPerOnce must not exceed maxHoldFreePoint");
    }
}
