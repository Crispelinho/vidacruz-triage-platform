package com.vidacruz.triageservice.domain.report;

import reactor.core.publisher.Mono;

/**
 * Computes the operation report where the data lives. Aggregates are never calculated by iterating patients in
 * memory (RF-05).
 */
public interface OperationReportRepository {

	Mono<OperationReport> operationReport(ReportPeriod period);
}
