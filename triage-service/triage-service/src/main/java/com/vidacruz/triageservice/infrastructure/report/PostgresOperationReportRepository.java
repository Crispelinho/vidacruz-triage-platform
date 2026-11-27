package com.vidacruz.triageservice.infrastructure.report;

import com.vidacruz.triageservice.domain.report.OperationReport;
import com.vidacruz.triageservice.domain.report.OperationReportRepository;
import com.vidacruz.triageservice.domain.report.ReportPeriod;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Operation report (RF-05) computed by PostgreSQL in one query. The application receives one row per UTC hour plus
 * the whole-range row, never the patients.
 */
@Repository
public class PostgresOperationReportRepository implements OperationReportRepository {

	private final DatabaseClient client;

	public PostgresOperationReportRepository(DatabaseClient client) {
		this.client = client;
	}

	@Override
	public Mono<OperationReport> operationReport(ReportPeriod period) {
		return client.sql(OperationReportSql.HOURLY_WITH_SUMMARY)
				.bind("from", period.from())
				.bind("to", exclusiveEnd(period))
				.map(OperationReportRows::toRow)
				.all()
				.collectList()
				.map(rows -> OperationReportRows.toReport(period, rows));
	}

	/** Both natural dates are inclusive: the query bound is the start of the UTC day after "hasta" (OD-4). */
	static Instant exclusiveEnd(ReportPeriod period) {
		return period.to().atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
				.atStartOfDay(ZoneOffset.UTC).toInstant();
	}
}
