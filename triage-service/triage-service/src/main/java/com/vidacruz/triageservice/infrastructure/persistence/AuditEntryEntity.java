package com.vidacruz.triageservice.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Append-only RN-04 audit row. Client-supplied UUIDs require explicit new-state tracking. */
@Table("auditoria_clinica")
public class AuditEntryEntity implements Persistable<UUID> {

	@Id
	private final UUID id;
	@Column("paciente_id")
	private final UUID patientId;
	@Column("creado_en")
	private final Instant createdAt;
	@Column("estado_anterior")
	private final String previousStatus;
	@Column("estado_nuevo")
	private final String nextStatus;
	@Column("tipo_evento")
	private final String eventType;

	@Transient
	private boolean isNew = true;

	@PersistenceCreator
	public AuditEntryEntity(UUID id, UUID patientId, Instant createdAt, String previousStatus,
			String nextStatus, String eventType) {
		this.id = id;
		this.patientId = patientId;
		this.createdAt = createdAt;
		this.previousStatus = previousStatus;
		this.nextStatus = nextStatus;
		this.eventType = eventType;
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	public UUID id() {
		return id;
	}

	public UUID patientId() {
		return patientId;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public String previousStatus() {
		return previousStatus;
	}

	public String nextStatus() {
		return nextStatus;
	}

	public String eventType() {
		return eventType;
	}
}
