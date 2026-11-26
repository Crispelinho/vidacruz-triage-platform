package com.vidacruz.triageservice.application.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/** API-06 hourly bucket (RF-05). */
public record OperationReportBucket(
		@JsonProperty("hora") String hora,
		@JsonProperty("totalRegistrados") long totalRegistrados,
		@JsonProperty("porNivel") Map<String, Long> porNivel,
		@JsonProperty("promedioEsperaMinutos") BigDecimal promedioEsperaMinutos,
		@JsonProperty("p95EsperaMinutos") BigDecimal p95EsperaMinutos) {
}
