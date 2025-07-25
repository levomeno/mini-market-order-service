package com.zad.minimarket.exception;

public class PriceFeedException extends RuntimeException {
    
    public PriceFeedException(String message) {
        super(message);
    }
    
    public PriceFeedException(String message, Throwable cause) {
        super(message, cause);
    }
}

