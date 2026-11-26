package com.vidacruz.clinical.domain.exceptions;

/** Raised when a {@code PromptRequest} violates its invariants. Mapped to HTTP 400 at the edge. */
public class InvalidPromptException extends IllegalArgumentException {

	public InvalidPromptException(String message) {
		super(message);
	}

}
