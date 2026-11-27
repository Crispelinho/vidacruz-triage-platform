package com.vidacruz.triageservice.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** RF-05 time-in-state percentiles (minutes) for one triage level. */
public record LatencyPercentileReport(
		@JsonProperty("nivel") String nivel,
		@JsonProperty("p50") double p50Minutes,
		@JsonProperty("p95") double p95Minutes,
		@JsonProperty("p99") double p99Minutes) {
}
