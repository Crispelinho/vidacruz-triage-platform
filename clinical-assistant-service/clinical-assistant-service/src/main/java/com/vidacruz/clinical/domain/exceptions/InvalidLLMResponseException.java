package com.vidacruz.clinical.domain.exceptions;

/**
 * The LLM output could not be parsed as the G-04 JSON contract. The answer is rejected directly; no regeneration is
 * attempted. Messages never contain the raw model output (CA-G-16).
 */
public class InvalidLLMResponseException extends RuntimeException {

	public InvalidLLMResponseException(String message) {
		super(message);
	}

	public InvalidLLMResponseException(String message, Throwable cause) {
		super(message, cause);
	}

}
