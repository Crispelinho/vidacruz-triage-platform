package com.vidacruz.triageservice.infrastructure.report;

/**
 * SQL of the operation report (RF-05). PostgreSQL computes every figure; the adapter only maps rows.
 *
 * <ul>
 *     <li>{@code hours}: every UTC hour of {@code [:from, :to)}, hours without patients included.</li>
 *     <li>{@code registered}: patients whose registration falls in the range. {@code :to} is the start of the UTC day
 *     after the requested {@code hasta}, so both natural dates are included (OD-4).</li>
 *     <li>{@code first_consultation}: first {@code ENTRADA_CONSULTA} of each registered patient, from the clinical
 *     audit ({@code ROW_NUMBER() OVER (PARTITION BY ...)}).</li>
 *     <li>{@code waits}: minutes from registration to that first entry, null for patients never attended.</li>
 *     <li>Final select: counts per level with {@code FILTER}, average and 95th percentile of the wait, per hour
 *     and for the whole range at once ({@code GROUPING SETS}). The whole-range percentile is computed over patients,
 *     because hourly percentiles cannot be combined.</li>
 * </ul>
 */
final class OperationReportSql {

	static final String HOURLY_WITH_SUMMARY = """
			WITH period AS (
			    SELECT CAST(:from AS timestamptz) AS starts, CAST(:to AS timestamptz) AS ends
			),
			hours AS (
			    SELECT series.hour
			      FROM period
			     CROSS JOIN generate_series(date_trunc('hour', period.starts, 'UTC'), period.ends,
			                                INTERVAL '1 hour') AS series (hour)
			     WHERE series.hour < period.ends
			),
			registered AS (
			    SELECT p.id, p.nivel, p.fecha_registro, date_trunc('hour', p.fecha_registro, 'UTC') AS hour
			      FROM paciente p
			      JOIN period ON p.fecha_registro >= period.starts AND p.fecha_registro < period.ends
			),
			first_consultation AS (
			    SELECT ranked.paciente_id, ranked.creado_en
			      FROM (SELECT a.paciente_id, a.creado_en,
			                   ROW_NUMBER() OVER (PARTITION BY a.paciente_id ORDER BY a.creado_en, a.id) AS entry
			              FROM auditoria_clinica a
			              JOIN registered r ON r.id = a.paciente_id
			             WHERE a.tipo_evento = 'ENTRADA_CONSULTA') AS ranked
			     WHERE ranked.entry = 1
			),
			waits AS (
			    SELECT h.hour, r.id, r.nivel,
			           EXTRACT(EPOCH FROM fc.creado_en - r.fecha_registro) / 60 AS wait_minutes
			      FROM hours h
			      LEFT JOIN registered r ON r.hour = h.hour
			      LEFT JOIN first_consultation fc ON fc.paciente_id = r.id
			)
			SELECT w.hour,
			       GROUPING(w.hour) = 1 AS whole_range,
			       COUNT(w.id) AS registered,
			       COUNT(w.id) FILTER (WHERE w.nivel = 'CRITICO') AS critico,
			       COUNT(w.id) FILTER (WHERE w.nivel = 'URGENTE') AS urgente,
			       COUNT(w.id) FILTER (WHERE w.nivel = 'URGENTE_2') AS urgente_2,
			       COUNT(w.id) FILTER (WHERE w.nivel = 'NO_URGENTE') AS no_urgente,
			       COUNT(w.id) FILTER (WHERE w.nivel = 'NO_URGENTE_2') AS no_urgente_2,
			       COUNT(w.wait_minutes) AS attended,
			       ROUND(AVG(w.wait_minutes), 2) AS promedio_espera_minutos,
			       ROUND(CAST(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY w.wait_minutes) AS numeric), 2)
			           AS p95_espera_minutos
			  FROM waits w
			 GROUP BY GROUPING SETS ((w.hour), ())
			 ORDER BY whole_range, w.hour""";

	private OperationReportSql() {
	}
}
