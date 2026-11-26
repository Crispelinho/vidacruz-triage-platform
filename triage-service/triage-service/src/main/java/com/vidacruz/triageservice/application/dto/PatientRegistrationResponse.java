package com.vidacruz.triageservice.application.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/** RF-01 response body. Wire keys match the technical test; Java accessors are English. */
public record PatientRegistrationResponse(
		@JsonProperty("id") UUID id,
		@JsonProperty("nivel") String level,
		@JsonProperty("slaMinutos") int slaMinutes,
		@JsonProperty("estado") String status,
		@JsonProperty("fechaRegistro") Instant registeredAt) {
}
