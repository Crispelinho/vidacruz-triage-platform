package com.vidacruz.triageservice.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** API-02 request body. The wire key is Spanish; the Java accessor is English. */
public record StateChangeRequest(@JsonProperty("estado") String status) {
}
