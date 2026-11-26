package com.vidacruz.clinical.infrastructure.adapters;

/**
 * Raised when Redis cannot serve the urgency projection, so the API can answer 503 instead of an opaque error.
 */
public class UrgencyStateUnavailableException extends RuntimeException {

    public UrgencyStateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
