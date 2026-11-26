package com.vidacruz.triageservice.infrastructure.persistence;

import java.time.Instant;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;

/** RF-05 read-only projection. All grouping and percentiles are computed by PostgreSQL. */
public interface OperationReportRepository extends Repository<OperationReportRow, Instant> {

	@Query("""
			WITH registrados AS (
			    SELECT a.paciente_id, a.creado_en AS registrado_en, p.nivel
			    FROM auditoria_clinica a
			    JOIN paciente p ON p.id = a.paciente_id
			    WHERE a.tipo_evento = 'REGISTRO'
			      AND a.creado_en >= :desde
			      AND a.creado_en < :hastaExclusive
			), consultas AS (
			    SELECT paciente_id, creado_en AS consulta_en,
			           ROW_NUMBER() OVER (PARTITION BY paciente_id ORDER BY creado_en) AS fila
			    FROM auditoria_clinica
			    WHERE tipo_evento = 'ENTRADA_CONSULTA'
			      AND creado_en >= :desde
			      AND creado_en < :hastaExclusive
			)
			SELECT
			    date_trunc('hour', r.registrado_en AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' AS hora,
			    COUNT(*) AS total_registrados,
			    COUNT(*) FILTER (WHERE r.nivel = 'CRITICO') AS critico,
			    COUNT(*) FILTER (WHERE r.nivel = 'URGENTE') AS urgente,
			    COUNT(*) FILTER (WHERE r.nivel = 'URGENTE_2') AS urgente_2,
			    COUNT(*) FILTER (WHERE r.nivel = 'NO_URGENTE') AS no_urgente,
			    COUNT(*) FILTER (WHERE r.nivel = 'NO_URGENTE_2') AS no_urgente_2,
			    AVG(EXTRACT(EPOCH FROM (c.consulta_en - r.registrado_en)) / 60.0)
			        FILTER (WHERE c.consulta_en IS NOT NULL) AS promedio_espera_minutos,
			    percentile_cont(0.95) WITHIN GROUP (
			        ORDER BY CASE WHEN c.consulta_en IS NULL THEN NULL
			                      ELSE EXTRACT(EPOCH FROM (c.consulta_en - r.registrado_en)) / 60.0 END
			    ) AS p95_espera_minutos
			FROM registrados r
			LEFT JOIN consultas c ON c.paciente_id = r.paciente_id AND c.fila = 1
			GROUP BY 1
			ORDER BY 1
			""")
	Flux<OperationReportRow> findHourly(@Param("desde") Instant desde,
			@Param("hastaExclusive") Instant hastaExclusive);
}
