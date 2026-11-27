package com.vidacruz.triageservice.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** NFR-20 error body. {@code field} is omitted unless the failure belongs to a query parameter. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
		@JsonProperty("code") String code,
		@JsonProperty("message") String message,
		@JsonProperty("field") String field) {

	public ApiError(String message) {
		this(null, message, null);
	}

	public ApiError(String code, String message) {
		this(code, message, null);
	}
}
