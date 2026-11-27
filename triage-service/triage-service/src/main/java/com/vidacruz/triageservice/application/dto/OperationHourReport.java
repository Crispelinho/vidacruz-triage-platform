package com.vidacruz.triageservice.application.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/** RF-05: registrations per UTC hour, with counts by triage level. */
public record OperationHourReport(
		@JsonProperty("hour") Instant hour,
		@JsonProperty("total") long total,
		@JsonProperty("by_level") Map<String, Long> byLevel) {
}
