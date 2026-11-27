package com.vidacruz.triageservice.domain.exception;

/** TRG-004: the acknowledgement request is malformed. {@code field} names the offending JSON property. */
public class InvalidAlertRequestException extends RuntimeException {

	public static final String CODE = "TRG-004";

	private final String field;

	public InvalidAlertRequestException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String field() {
		return field;
	}
}
