package com.study.point.domain.point.vo;

import com.study.point.domain.point.exception.InsufficientPointException;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PointBalance {
    private long available;

    public PointBalance add(long amount) {
        return new PointBalance(this.available + amount);
    }

    public PointBalance subtract(long amount) {
        if (amount > this.available) {
            throw new InsufficientPointException("Not enough points to use");
        }
        return new PointBalance(this.available - amount);
    }
}
