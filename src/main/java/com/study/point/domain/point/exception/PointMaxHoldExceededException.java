package com.study.point.domain.point.exception;

public class PointMaxHoldExceededException extends RuntimeException {
    public PointMaxHoldExceededException(String message) {
        super(message);
    }
}
