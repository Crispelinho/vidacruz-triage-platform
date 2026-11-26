package com.vidacruz.triageservice.infrastructure.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationReportSqlTest {

	@Test
	void computesEveryFigureInPostgresOverTheTargetSchema() {
		var sql = OperationReportSql.HOURLY_WITH_SUMMARY;

		assertThat(sql)
				.contains("paciente p")
				.contains("auditoria_clinica a")
				.contains("ENTRADA_CONSULTA")
				.contains("ROW_NUMBER() OVER")
				.contains("p.fecha_registro >= period.starts")
				.contains("p.fecha_registro < period.ends")
				.contains("generate_series")
				.contains("COUNT(w.id) FILTER")
				.contains("PERCENTILE_CONT(0.95)")
				.contains("GROUP BY GROUPING SETS ((w.hour), ())");
	}
}
