package com.vidacruz.triageservice.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** RF-05 escalation summary for one triage level. {@code escalatedPct} is a 0-100 percentage. */
public record SlaOperationalReport(
		@JsonProperty("nivel") String nivel,
		@JsonProperty("total") long total,
		@JsonProperty("escalated_count") long escalatedCount,
		@JsonProperty("escalated_pct") double escalatedPct) {
}
