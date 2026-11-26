package com.vidacruz.triageservice.application.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vidacruz.triageservice.domain.exception.FieldError;

/** NFR-20 error body. Wire keys stay in the contract's English/camelCase form. */
public record ApiError(
		@JsonProperty("code") String code,
		@JsonProperty("message") String message,
		@JsonProperty("errors") List<FieldError> errors,
		@JsonProperty("correlationId") String correlationId) {

	public ApiError(String code, String message, String correlationId) {
		this(code, message, List.of(), correlationId);
	}

	public ApiError(String message) {
		this(null, message, List.of(), null);
	}
}
