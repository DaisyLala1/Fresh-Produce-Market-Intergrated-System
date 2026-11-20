package com.freshproduce.pos.exception;

/** An external dependency (the AI forecasting service) cannot be reached (HTTP 503). */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
