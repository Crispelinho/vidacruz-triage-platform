package com.vidacruz.triageservice.domain.exception;

/** NFR-20 field-level validation error; values stay in Spanish for API clients. */
public record FieldError(String campo, String mensaje) {
}
