package com.vidacruz.triageservice.domain.dto;

import java.time.Instant;
import java.util.Map;

/** RF-05: registrations per UTC hour, with counts by triage level. */
public record OperationHourReport(
		Instant hour,
		long total,
		Map<String, Long> byLevel) {
}
