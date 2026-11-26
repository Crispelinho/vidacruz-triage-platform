package com.vidacruz.triageservice.domain.exception;

/** RF-01 validation failure; the message names the invalid field for the API client. */
public class InvalidPatientDataException extends RuntimeException {

	public InvalidPatientDataException(String message) {
		super(message);
	}
}
