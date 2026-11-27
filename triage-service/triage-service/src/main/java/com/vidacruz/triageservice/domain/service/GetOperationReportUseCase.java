package com.vidacruz.triageservice.domain.service;

import com.vidacruz.triageservice.domain.report.OperationReport;
import com.vidacruz.triageservice.domain.report.ReportPolicy;
import com.vidacruz.triageservice.domain.report.OperationReportRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * RF-05: operation report of a range, grouped by UTC hour. The range is checked before any query runs.
 */
@Service
public class GetOperationReportUseCase {

	private final OperationReportRepository operationReportRepository;
	private final ReportPolicy reportPolicy;

	public GetOperationReportUseCase(OperationReportRepository operationReportRepository, ReportPolicy reportPolicy) {
		this.operationReportRepository = operationReportRepository;
		this.reportPolicy = reportPolicy;
	}

	public Mono<OperationReport> getOperationReport(Instant desde, Instant hasta) {
		return Mono.fromCallable(() -> reportPolicy.periodBetween(desde, hasta))
				.flatMap(operationReportRepository::operationReport);
	}
}
