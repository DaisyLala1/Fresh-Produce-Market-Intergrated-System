package com.freshproduce.pos.exception;

/** A business rule was violated (HTTP 400), e.g. insufficient stock or an inactive product. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
