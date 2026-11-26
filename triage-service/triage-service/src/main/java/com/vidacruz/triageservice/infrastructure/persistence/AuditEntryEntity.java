package com.vidacruz.triageservice.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Append-only RN-04 audit row. */
@Table("auditoria_clinica")
public record AuditEntryEntity(
		@Id UUID id,
		@Column("paciente_id") UUID patientId,
		@Column("creado_en") Instant createdAt,
		@Column("estado_anterior") String previousStatus,
		@Column("estado_nuevo") String nextStatus,
		@Column("tipo_evento") String eventType) {
}
