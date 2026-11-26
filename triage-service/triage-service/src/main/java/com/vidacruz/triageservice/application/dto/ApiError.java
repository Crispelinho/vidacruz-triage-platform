package com.vidacruz.triageservice.application.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vidacruz.triageservice.domain.exception.FieldError;

/** NFR-20 error body. Null optional members are omitted from the wire representation. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
		@JsonProperty("code") String code,
		@JsonProperty("message") String message,
		@JsonProperty("field") String field,
		@JsonProperty("errors") List<FieldError> errors,
		@JsonProperty("correlationId") String correlationId) {

	public ApiError(String message) {
		this(null, message, null, List.of(), null);
	}

	public ApiError(String code, String message) {
		this(code, message, null, List.of(), null);
	}

	/** Constructor used by parameter-validation failures that must report the offending field. */
	public ApiError(String code, String message, String field) {
		this(code, message, field, List.of(), null);
	}

	public ApiError(String code, String message, List<FieldError> errors, String correlationId) {
		this(code, message, null, errors, correlationId);
	}
}
