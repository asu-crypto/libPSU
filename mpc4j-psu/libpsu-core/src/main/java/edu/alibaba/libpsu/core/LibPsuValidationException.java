package edu.alibaba.libpsu.core;

/**
 * Raised when libPSU parameter or wire validation fails.
 */
public class LibPsuValidationException extends IllegalArgumentException {
    public LibPsuValidationException(String message) {
        super(message);
    }

    public LibPsuValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
