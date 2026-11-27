package com.vidacruz.triageservice.application.dto;

/** RF-05 escalation summary for one triage level. {@code escalatedPct} is a 0-100 percentage. */
public record SlaOperationalReport(
		String nivel,
		long total,
		long escalatedCount,
		double escalatedPct) {
}
