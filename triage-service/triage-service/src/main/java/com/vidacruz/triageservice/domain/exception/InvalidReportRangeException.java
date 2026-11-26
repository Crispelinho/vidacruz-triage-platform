package com.vidacruz.triageservice.domain.exception;

/** RF-05 range validation failure; the field names the offending query parameter. */
public class InvalidReportRangeException extends RuntimeException {

	private final String field;

	public InvalidReportRangeException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String field() {
		return field;
	}
}
