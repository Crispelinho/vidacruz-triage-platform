package com.vidacruz.triageservice.domain.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.vidacruz.triageservice.application.dto.OperationReportBucket;
import com.vidacruz.triageservice.domain.exception.InvalidReportRangeException;
import com.vidacruz.triageservice.infrastructure.persistence.OperationReportRepository;
import com.vidacruz.triageservice.infrastructure.persistence.OperationReportRow;

import reactor.core.publisher.Flux;

/** RF-05 use case: validate the UTC range and project the pre-aggregated SQL rows into API-06. */
@Service
public class OperationReportService {

	private static final int MAX_CALENDAR_DAYS = 90;

	private final OperationReportRepository repository;

	public OperationReportService(OperationReportRepository repository) {
		this.repository = repository;
	}

	public Flux<OperationReportBucket> hourly(String desde, String hasta) {
		Instant start = parse(desde, "desde");
		Instant end = parse(hasta, "hasta");
		LocalDate startDate = startDate(start);
		LocalDate endDate = startDate(end);

		if (start.isAfter(end) || startDate.isAfter(endDate)) {
			throw new InvalidReportRangeException("desde", "desde no puede ser posterior a hasta");
		}
		long calendarDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
		if (calendarDays > MAX_CALENDAR_DAYS) {
			throw new InvalidReportRangeException("hasta", "el rango no puede superar 90 días naturales");
		}
		Instant endExclusive = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

		return repository.findHourly(start, endExclusive).map(this::toBucket);
	}

	private static Instant parse(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new InvalidReportRangeException(field, field + " es obligatorio");
		}
		try {
			return OffsetDateTime.parse(value).toInstant();
		} catch (DateTimeParseException exception) {
			throw new InvalidReportRangeException(field, field + " debe ser ISO 8601 con desplazamiento");
		}
	}

	private static LocalDate startDate(Instant instant) {
		return instant.atZone(ZoneOffset.UTC).toLocalDate();
	}

	private OperationReportBucket toBucket(OperationReportRow row) {
		Map<String, Long> counts = new LinkedHashMap<>();
		counts.put("CRITICO", row.critico());
		counts.put("URGENTE", row.urgente());
		counts.put("URGENTE_2", row.urgente2());
		counts.put("NO_URGENTE", row.noUrgente());
		counts.put("NO_URGENTE_2", row.noUrgente2());
		return new OperationReportBucket(
				row.hora().toInstant().toString(),
				row.totalRegistrados(),
				counts,
				row.promedioEsperaMinutos(),
				row.p95EsperaMinutos());
	}
}
