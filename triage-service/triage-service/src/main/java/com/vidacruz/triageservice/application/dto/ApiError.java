package com.vidacruz.triageservice.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiError(@JsonProperty("message") String message) {
}
