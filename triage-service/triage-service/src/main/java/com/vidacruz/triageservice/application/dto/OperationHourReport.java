package com.vidacruz.triageservice.application.dto;

import java.time.Instant;

/** RF-05 hourly operation view: registrations per triage level in each UTC hour bucket. */
public record OperationHourReport(
		Instant hour,
		long criticalCount,
		long urgenteCount,
		long urgente2Count,
		long noUrgenteCount,
		long noUrgente2Count) {
}
