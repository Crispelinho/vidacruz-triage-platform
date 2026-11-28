package com.vidacruz.triageservice.domain.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import com.vidacruz.triageservice.domain.dto.LatencyPercentileReport;
import com.vidacruz.triageservice.domain.dto.OperationHourReport;
import com.vidacruz.triageservice.domain.dto.SlaOperationalReport;
import com.vidacruz.triageservice.domain.port.ReportQueryRepository;

import reactor.core.publisher.Flux;

/**
 * RF-05 reports. Date parsing and the inclusive range contract stay in the domain; SQL lives behind
 * {@link ReportQueryRepository}. All time bucketing is done in UTC.
 */
public class ReportService {

	private final ReportQueryRepository reports;

	public ReportService(ReportQueryRepository reports) {
		this.reports = reports;
	}

	public Flux<OperationHourReport> operationByHour(String from, String to) {
		return reports.operationByHour(parseInstant(from, false), parseInstant(to, true));
	}

	public Flux<LatencyPercentileReport> percentileLatencyByLevel(String from, String to) {
		return reports.latencyByLevel(parseInstant(from, false), parseInstant(to, true));
	}

	public Flux<SlaOperationalReport> operationalSla(String from, String to) {
		return reports.slaOperational(parseInstant(from, false), parseInstant(to, true));
	}

	private static Instant parseInstant(String value, boolean upperBound) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Instant.parse(value);
		} catch (DateTimeParseException ignored) {
			// try offset datetime
		}
		try {
			return OffsetDateTime.parse(value).toInstant();
		} catch (DateTimeParseException ignored) {
			// try local date
		}
		try {
			LocalDate date = LocalDate.parse(value);
			return upperBound
					? date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1)
					: date.atStartOfDay().toInstant(ZoneOffset.UTC);
		} catch (DateTimeParseException ex) {
			throw new IllegalArgumentException("Invalid date parameter: " + value, ex);
		}
	}
}
