package com.vidacruz.triageservice.domain.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

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
			SELECT date_trunc('hour', fecha_registro AT TIME ZONE 'UTC') AS hour,
			       COUNT(*) FILTER (WHERE nivel = 'CRITICO')      AS critico,
			       COUNT(*) FILTER (WHERE nivel = 'URGENTE')      AS urgente,
			       COUNT(*) FILTER (WHERE nivel = 'URGENTE_2')    AS urgente_2,
			       COUNT(*) FILTER (WHERE nivel = 'NO_URGENTE')   AS no_urgente,
			       COUNT(*) FILTER (WHERE nivel = 'NO_URGENTE_2') AS no_urgente_2
			FROM paciente
			WHERE 1 = 1
			%s
			GROUP BY date_trunc('hour', fecha_registro AT TIME ZONE 'UTC')
			ORDER BY hour ASC
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
			    WHERE estado = 'EN_CONSULTA' OR EXISTS (
			        SELECT 1 FROM auditoria_clinica a
			        WHERE a.paciente_id = paciente.id AND a.tipo_evento = 'ENTRADA_CONSULTA'
			    )
			    %s
			) t
			GROUP BY nivel
			ORDER BY %s
			""";

	private static final String SLA_OPERATIONAL_SQL = """
			SELECT nivel,
			       COUNT(*)                              AS total,
			       COUNT(*) FILTER (WHERE escalado)      AS escalated,
			       ROUND(100.0 * COUNT(*) FILTER (WHERE escalado) / NULLIF(COUNT(*), 0), 2)::double precision AS escalated_pct
			FROM paciente
			WHERE 1 = 1
			%s
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

	public Flux<OperationHourReport> operationByHour(String from, String to) {
		Instant fromInstant = parseInstant(from, false);
		Instant toInstant = parseInstant(to, true);
		StringBuilder filter = new StringBuilder();
		if (fromInstant != null) {
			filter.append("AND fecha_registro >= :from\n");
		}
		if (toInstant != null) {
			filter.append("AND fecha_registro <= :to\n");
		}
		DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(OPERATION_BY_HOUR_SQL.formatted(filter));
		if (fromInstant != null) {
			spec = spec.bind("from", fromInstant);
		}
		if (toInstant != null) {
			spec = spec.bind("to", toInstant);
		}
		return spec.map((row, meta) -> {
			long critico = value(row.get("critico", Long.class));
			long urgente = value(row.get("urgente", Long.class));
			long urgente2 = value(row.get("urgente_2", Long.class));
			long noUrgente = value(row.get("no_urgente", Long.class));
			long noUrgente2 = value(row.get("no_urgente_2", Long.class));
			long total = critico + urgente + urgente2 + noUrgente + noUrgente2;
			Map<String, Long> byLevel = new LinkedHashMap<>();
			byLevel.put("CRITICO", critico);
			byLevel.put("URGENTE", urgente);
			byLevel.put("URGENTE_2", urgente2);
			byLevel.put("NO_URGENTE", noUrgente);
			byLevel.put("NO_URGENTE_2", noUrgente2);
			Instant hour = toInstant(row.get("hour", Object.class));
			return new OperationHourReport(hour, total, byLevel);
		}).all();
	}

	public Flux<LatencyPercentileReport> percentileLatencyByLevel(String from, String to) {
		String filter = rangeFilter(from, to, "fecha_registro");
		DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(LATENCY_PERCENTILES_SQL.formatted(filter, SEVERITY_ORDER));
		spec = bindRange(spec, from, to);
		return spec.map((row, meta) -> new LatencyPercentileReport(
				row.get("nivel", String.class),
				asDouble(row.get("p50", Object.class)),
				asDouble(row.get("p95", Object.class)),
				asDouble(row.get("p99", Object.class))))
				.all();
	}

	public Flux<SlaOperationalReport> operationalSla(String from, String to) {
		String filter = rangeFilter(from, to, "fecha_registro");
		DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(SLA_OPERATIONAL_SQL.formatted(filter, SEVERITY_ORDER));
		spec = bindRange(spec, from, to);
		return spec.map((row, meta) -> new SlaOperationalReport(
				row.get("nivel", String.class),
				value(row.get("total", Long.class)),
				value(row.get("escalated", Long.class)),
				asDouble(row.get("escalated_pct", Object.class))))
				.all();
	}

	private static String rangeFilter(String from, String to, String column) {
		StringBuilder filter = new StringBuilder();
		if (parseInstant(from, false) != null) {
			filter.append("AND ").append(column).append(" >= :from\n");
		}
		if (parseInstant(to, true) != null) {
			filter.append("AND ").append(column).append(" <= :to\n");
		}
		return filter.toString();
	}

	private static DatabaseClient.GenericExecuteSpec bindRange(DatabaseClient.GenericExecuteSpec spec, String from, String to) {
		Instant fromInstant = parseInstant(from, false);
		Instant toInstant = parseInstant(to, true);
		if (fromInstant != null) {
			spec = spec.bind("from", fromInstant);
		}
		if (toInstant != null) {
			spec = spec.bind("to", toInstant);
		}
		return spec;
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

	private static Instant toInstant(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Instant instant) {
			return instant;
		}
		if (value instanceof OffsetDateTime odt) {
			return odt.toInstant();
		}
		if (value instanceof java.time.LocalDateTime ldt) {
			return ldt.toInstant(ZoneOffset.UTC);
		}
		return Instant.parse(value.toString().replace(' ', 'T') + (value.toString().endsWith("Z") ? "" : "Z"));
	}

	private static double asDouble(Object value) {
		if (value == null) {
			return 0d;
		}
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		return Double.parseDouble(value.toString());
	}

	private static long value(Long value) {
		return value == null ? 0L : value;
	}
}
