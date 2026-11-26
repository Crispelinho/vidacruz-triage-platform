package com.vidacruz.triageservice.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiError(
		@JsonProperty("code") String code,
		@JsonProperty("message") String message) {

	public ApiError(String message) {
		this(null, message);
	}
}
