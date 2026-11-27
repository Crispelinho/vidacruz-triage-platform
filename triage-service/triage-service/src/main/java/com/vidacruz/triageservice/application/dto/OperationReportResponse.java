package com.vidacruz.triageservice.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/** Operation report of RF-05: every UTC hour of the range and the figures of the whole range. */
public record OperationReportResponse(
		@JsonProperty("desde") Instant from,
		@JsonProperty("hasta") Instant to,
		@JsonProperty("horas") List<HourlyOperationResponse> hours,
		@JsonProperty("resumen") OperationSummaryResponse summary) {
}
