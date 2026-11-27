package com.vidacruz.clinical.domain.exceptions;

/**
 * The LLM answer cites evidence that was not retrieved (a marker with no matching fragment). The answer is discarded
 * instead of being shown with an invented source. Never carries answer text (CA-G-16).
 */
public class InvalidCitationException extends RuntimeException {

	public InvalidCitationException(String message) {
		super(message);
	}

}
