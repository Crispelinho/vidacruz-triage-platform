package com.vidacruz.triageservice.domain.exception;

/** RF-02 invalid transition; mapped to HTTP 409. */
public class InvalidStateTransitionException extends RuntimeException {

	public InvalidStateTransitionException(String message) {
		super(message);
	}
}
