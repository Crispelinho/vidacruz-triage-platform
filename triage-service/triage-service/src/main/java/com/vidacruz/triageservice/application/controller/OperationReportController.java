package com.vidacruz.triageservice.application.controller;

import com.vidacruz.triageservice.application.dto.OperationReportResponse;
import com.vidacruz.triageservice.domain.exception.InvalidReportRequestException;
import com.vidacruz.triageservice.domain.service.GetOperationReportUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/** RF-05 / API-06: reads the inclusive ISO 8601 range from the query and returns the PostgreSQL-built report. */
@RestController
@RequestMapping("/triage/reportes")
public class OperationReportController {

	private final GetOperationReportUseCase getOperationReport;

	public OperationReportController(GetOperationReportUseCase getOperationReport) {
		this.getOperationReport = getOperationReport;
	}

	@GetMapping("/operacion")
	public Mono<OperationReportResponse> operationReport(
			@RequestParam(name = "desde", required = false) String desde,
			@RequestParam(name = "hasta", required = false) String hasta) {
		return Mono.fromCallable(() -> parse(desde, hasta))
				.flatMap(range -> getOperationReport.getOperationReport(range[0], range[1]))
				.map(OperationReportMapper::toResponse);
	}

	/** An offset or {@code Z} is required: a local date-time would make the UTC range ambiguous (DEC-11). */
	private static Instant[] parse(String desde, String hasta) {
		return new Instant[] { instant(desde, "desde"), instant(hasta, "hasta") };
	}

	private static Instant instant(String value, String field) {
		if (value == null || value.isBlank()) {
			throw InvalidReportRequestException.missing(field);
		}
		try {
			return OffsetDateTime.parse(value).toInstant();
		} catch (DateTimeParseException error) {
			throw InvalidReportRequestException.malformed(field);
		}
	}
}
