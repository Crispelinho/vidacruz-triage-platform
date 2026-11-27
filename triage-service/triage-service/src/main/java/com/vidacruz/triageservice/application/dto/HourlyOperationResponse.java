package com.vidacruz.triageservice.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Figures of the patients registered during one UTC hour. Null averages are serialized explicitly (G-T14). */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record HourlyOperationResponse(
		@JsonProperty("hora") Instant hour,
		@JsonProperty("totalRegistrados") long registered,
		@JsonProperty("porNivel") Map<String, Long> registeredByLevel,
		@JsonProperty("atendidos") long attended,
		@JsonProperty("promedioEsperaMinutos") BigDecimal averageWaitMinutes,
		@JsonProperty("p95EsperaMinutos") BigDecimal p95WaitMinutes) {
}
