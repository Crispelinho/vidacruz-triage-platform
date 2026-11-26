package com.vidacruz.triageservice.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.springframework.data.relational.core.mapping.Column;

/** Flat SQL projection for RF-05; aggregation happens entirely in PostgreSQL. */
public record OperationReportRow(
		@Column("hora") OffsetDateTime hora,
		@Column("total_registrados") long totalRegistrados,
		@Column("critico") long critico,
		@Column("urgente") long urgente,
		@Column("urgente_2") long urgente2,
		@Column("no_urgente") long noUrgente,
		@Column("no_urgente_2") long noUrgente2,
		@Column("promedio_espera_minutos") BigDecimal promedioEsperaMinutos,
		@Column("p95_espera_minutos") BigDecimal p95EsperaMinutos) {
}
