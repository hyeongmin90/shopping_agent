package com.shopping.order.exception;

public class ApiException extends RuntimeException {
    public ApiException(String message) {
        super(message);
    }
}
