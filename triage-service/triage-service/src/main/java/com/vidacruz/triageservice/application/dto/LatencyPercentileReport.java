package com.vidacruz.triageservice.application.dto;

/** RF-05 time-in-state percentiles (minutes) for one triage level. */
public record LatencyPercentileReport(
		String nivel,
		double p50Minutes,
		double p95Minutes,
		double p99Minutes) {
}
