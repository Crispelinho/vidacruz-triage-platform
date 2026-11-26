package com.vidacruz.triageservice.application.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/** API-02/API-03 patient view. Wire keys follow the technical test; Java accessors are English. */
public record PatientDetailResponse(
		@JsonProperty("id") UUID id,
		@JsonProperty("nombreCompleto") String fullName,
		@JsonProperty("nivel") String level,
		@JsonProperty("slaMinutos") int slaMinutes,
		@JsonProperty("estado") String status,
		@JsonProperty("escalado") boolean escalated,
		@JsonProperty("fechaRegistro") Instant registeredAt,
		@JsonProperty("tiempoEnEstadoMinutos") long minutesInStatus) {
}
