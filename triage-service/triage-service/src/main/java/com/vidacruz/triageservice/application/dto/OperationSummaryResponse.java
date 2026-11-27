package com.vidacruz.triageservice.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;

/** Figures of the whole report range, computed over patients rather than over hourly percentiles. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OperationSummaryResponse(
		@JsonProperty("totalRegistrados") long registered,
		@JsonProperty("porNivel") Map<String, Long> registeredByLevel,
		@JsonProperty("atendidos") long attended,
		@JsonProperty("promedioEsperaMinutos") BigDecimal averageWaitMinutes,
		@JsonProperty("p95EsperaMinutos") BigDecimal p95WaitMinutes) {
}
