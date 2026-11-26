package com.vidacruz.clinical.domain.exceptions;

/**
 * The LLM could not answer: timeout, connection failure or (later) an open circuit breaker.
 * Mapped to HTTP 503 {@code LLM_UNAVAILABLE} (ADR-09, NFR-03). Never carries question or answer text (CA-G-16).
 */
public class LLMUnavailableException extends RuntimeException {

	public LLMUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

	public LLMUnavailableException(String message) {
		super(message);
	}

}
