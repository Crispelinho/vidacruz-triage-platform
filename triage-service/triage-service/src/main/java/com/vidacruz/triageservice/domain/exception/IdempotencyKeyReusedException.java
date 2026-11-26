package com.vidacruz.triageservice.domain.exception;

/** DEC-15: the same Idempotency-Key was reused with a different request body. */
public class IdempotencyKeyReusedException extends RuntimeException {

	public IdempotencyKeyReusedException(String key) {
		super("La clave de idempotencia ya fue utilizada con otro cuerpo: " + key);
	}
}
