package com.vidacruz.triageservice.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** RF-06 alert contract. It never carries the patient name or complaint (shared screens). */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AlertResponse(
		@JsonProperty("id") UUID id,
		@JsonProperty("pacienteId") UUID patientId,
		@JsonProperty("nivel") String level,
		@JsonProperty("slaMinutos") int slaMinutes,
		@JsonProperty("fechaRegistro") Instant registeredAt,
		@JsonProperty("fechaEscalamiento") Instant escalatedAt,
		@JsonProperty("minutosEsperaAlEscalar") BigDecimal waitMinutes,
		@JsonProperty("estado") String status,
		@JsonProperty("reconocidaPor") String responsible,
		@JsonProperty("fechaReconocimiento") Instant acknowledgedAt,
		@JsonProperty("fechaResolucion") Instant resolvedAt,
		@JsonProperty("actualizadaEn") Instant updatedAt) {
}
