package com.freshproduce.pos.exception;

/** Requested sale quantity exceeds the available eligible stock (UC-02 alternative flow 1). */
public class InsufficientStockException extends BusinessException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
