package com.vidacruz.triageservice.domain.service;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;

import com.vidacruz.triageservice.application.dto.LatencyPercentileReport;
import com.vidacruz.triageservice.application.dto.OperationHourReport;
import com.vidacruz.triageservice.application.dto.SlaOperationalReport;

import reactor.core.publisher.Flux;

/**
 * RF-05 reports. Plain SQL on PostgreSQL (ordered-set aggregates and FILTER) executed through the non-blocking
 * {@link DatabaseClient}. All time bucketing is done in UTC.
 */
@Service
public class ReportService {

	private static final String OPERATION_BY_HOUR_SQL = """
			SELECT date_trunc('hour', fecha_registro, 'UTC') AS hour,
			       COUNT(*) FILTER (WHERE nivel = 'CRITICO')      AS critico,
			       COUNT(*) FILTER (WHERE nivel = 'URGENTE')      AS urgente,
			       COUNT(*) FILTER (WHERE nivel = 'URGENTE_2')    AS urgente_2,
			       COUNT(*) FILTER (WHERE nivel = 'NO_URGENTE')   AS no_urgente,
			       COUNT(*) FILTER (WHERE nivel = 'NO_URGENTE_2') AS no_urgente_2
			FROM paciente
			WHERE 1 = 1
			%s
			GROUP BY date_trunc('hour', fecha_registro, 'UTC')
			ORDER BY hour DESC
			""";

	private static final String LATENCY_PERCENTILES_SQL = """
			SELECT nivel,
			       PERCENTILE_CONT(0.5)  WITHIN GROUP (ORDER BY minutes) AS p50,
			       PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY minutes) AS p95,
			       PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY minutes) AS p99
			FROM (
			    SELECT nivel,
			           GREATEST(EXTRACT(EPOCH FROM (ultimo_cambio_en - fecha_registro)) / 60.0, 0)::double precision AS minutes
			    FROM paciente
			) t
			GROUP BY nivel
			ORDER BY %s
			""";

	private static final String SLA_OPERATIONAL_SQL = """
			SELECT nivel,
			       COUNT(*)                              AS total,
			       COUNT(*) FILTER (WHERE escalado)      AS escalated,
			       ROUND(100.0 * COUNT(*) FILTER (WHERE escalado) / COUNT(*), 2)::double precision AS escalated_pct
			FROM paciente
			GROUP BY nivel
			ORDER BY %s
			""";

	/** Most severe level first (RN-01). */
	private static final String SEVERITY_ORDER = """
			CASE nivel WHEN 'CRITICO' THEN 1 WHEN 'URGENTE' THEN 2 WHEN 'URGENTE_2' THEN 3
			           WHEN 'NO_URGENTE' THEN 4 WHEN 'NO_URGENTE_2' THEN 5 ELSE 6 END""";

	private final DatabaseClient databaseClient;

	public ReportService(DatabaseClient databaseClient) {
		this.databaseClient = databaseClient;
	}

	/**
	 * Registrations per hour and triage level, newest hour first.
	 *
	 * @param from inclusive start day (UTC), or {@code null} for no lower bound
	 * @param to inclusive end day (UTC), or {@code null} for no upper bound
	 */
	public Flux<OperationHourReport> operationByHour(LocalDate from, LocalDate to) {
		StringBuilder filter = new StringBuilder();
		if (from != null) {
			filter.append("AND fecha_registro >= :from\n");
		}
		if (to != null) {
			filter.append("AND fecha_registro < :to\n");
		}
		DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(OPERATION_BY_HOUR_SQL.formatted(filter));
		if (from != null) {
			spec = spec.bind("from", from.atStartOfDay().toInstant(ZoneOffset.UTC));
		}
		if (to != null) {
			spec = spec.bind("to", to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
		}
		return spec.map((row, meta) -> new OperationHourReport(
				row.get("hour", java.time.Instant.class),
				row.get("critico", Long.class),
				row.get("urgente", Long.class),
				row.get("urgente_2", Long.class),
				row.get("no_urgente", Long.class),
				row.get("no_urgente_2", Long.class))).all();
	}

	/** p50/p95/p99 of minutes between registration and last state change, per triage level. */
	public Flux<LatencyPercentileReport> percentileLatencyByLevel() {
		return databaseClient.sql(LATENCY_PERCENTILES_SQL.formatted(SEVERITY_ORDER))
				.map((row, meta) -> new LatencyPercentileReport(
						row.get("nivel", String.class),
						row.get("p50", Double.class),
						row.get("p95", Double.class),
						row.get("p99", Double.class)))
				.all();
	}

	/** Escalated vs total patients per triage level. */
	public Flux<SlaOperationalReport> operationalSla() {
		return databaseClient.sql(SLA_OPERATIONAL_SQL.formatted(SEVERITY_ORDER))
				.map((row, meta) -> new SlaOperationalReport(
						row.get("nivel", String.class),
						row.get("total", Long.class),
						row.get("escalated", Long.class),
						row.get("escalated_pct", Double.class)))
				.all();
	}
}
