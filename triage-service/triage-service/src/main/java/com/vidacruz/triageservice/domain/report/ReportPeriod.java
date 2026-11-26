package com.vidacruz.triageservice.domain.report;

import java.time.Instant;
import java.util.Objects;

/**
 * Range of the operation report (API-06). Both ends are inclusive natural dates in UTC: {@code from} is the requested
 * {@code desde} instant and {@code to} is the requested {@code hasta} instant. The repository derives the exclusive
 * end as the start of the UTC day after {@code to}.
 */
public record ReportPeriod(Instant from, Instant to) {

	public ReportPeriod {
		Objects.requireNonNull(from, "desde");
		Objects.requireNonNull(to, "hasta");
	}
}
