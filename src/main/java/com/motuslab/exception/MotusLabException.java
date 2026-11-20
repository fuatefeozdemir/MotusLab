package com.motuslab.exception;

public abstract class MotusLabException extends RuntimeException {

    protected MotusLabException(String message) {
        super(message);
    }

    protected MotusLabException(String message, Throwable cause) {
        super(message, cause);
    }
}