package com.vidacruz.triageservice.domain.report;

import com.vidacruz.triageservice.domain.exception.InvalidReportRequestException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Limits of the operation report (OD-4). {@code desde} and {@code hasta} are inclusive UTC natural dates: a range from
 * day 1 to day 90 covers 90 dates and is accepted; day 1 to day 91 covers 91 dates and is rejected.
 *
 * @param maxRange longest range a report may cover
 */
public record ReportPolicy(Duration maxRange) {

	private static final long MINUTES_PER_HOUR = 60;
	private static final long MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

	public ReportPolicy {
		if (maxRange == null || maxRange.isNegative() || maxRange.isZero()) {
			throw new IllegalArgumentException("El rango máximo del reporte debe ser positivo: " + maxRange);
		}
	}

	/** Checks the range before any query runs. */
	public ReportPeriod periodBetween(Instant desde, Instant hasta) {
		Objects.requireNonNull(desde, "desde");
		Objects.requireNonNull(hasta, "hasta");
		if (!desde.isBefore(hasta)) {
			throw InvalidReportRequestException.invalidRange("desde",
					"la fecha desde debe ser anterior a la fecha hasta");
		}
		var days = ChronoUnit.DAYS.between(
				LocalDate.ofInstant(desde, ZoneOffset.UTC), LocalDate.ofInstant(hasta, ZoneOffset.UTC)) + 1;
		var maxDays = Math.max(1L, maxRange.toDays());
		// The exact bound also supports sub-day maxima; the day bound enforces the inclusive natural-date rule (OD-4).
		if (Duration.between(desde, hasta).compareTo(maxRange) > 0 || days > maxDays) {
			throw InvalidReportRequestException.invalidRange("hasta",
					"el rango no puede superar " + describe(maxRange));
		}
		return new ReportPeriod(desde, hasta);
	}

	/** The range in the largest whole unit: "90 días", "12 horas" or "90 minutos". */
	private static String describe(Duration range) {
		var minutes = range.toMinutes();
		if (minutes % MINUTES_PER_DAY == 0) {
			return minutes / MINUTES_PER_DAY + " días";
		}
		return minutes % MINUTES_PER_HOUR == 0 ? minutes / MINUTES_PER_HOUR + " horas" : minutes + " minutos";
	}
}
