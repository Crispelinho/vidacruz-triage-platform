package com.vidacruz.triageservice.domain.exception;

import java.util.List;

/** RF-01 validation failure; the field names and messages stay in Spanish for API clients. */
public class InvalidPatientDataException extends RuntimeException {

	private final List<FieldError> errors;

	public InvalidPatientDataException(String message) {
		this(message, List.of(new FieldError(null, message)));
	}

	public InvalidPatientDataException(String message, List<FieldError> errors) {
		super(message);
		this.errors = List.copyOf(errors);
	}

	public List<FieldError> errors() {
		return errors;
	}
}
