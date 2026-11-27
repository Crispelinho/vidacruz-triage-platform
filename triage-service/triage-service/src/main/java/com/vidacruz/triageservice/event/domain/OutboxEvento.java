package com.vidacruz.triageservice.event.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Table("outbox_evento")
public class OutboxEvento implements Persistable<UUID> {
    @Id
    @Column("event_id")
    private UUID eventId;

    @Transient
    private boolean isNew = true;

    @Column("event_type")
    private String eventType;

    @Column("payload")
    private String payload;

    @Column("paciente_id")
    private UUID pacienteId;

    @Column("paciente_sequence")
    private Integer pacienteSequence;

    @Column("occurred_at")
    private OffsetDateTime occurredAt;

    @Column("correlation_id")
    private UUID correlationId;

    /** W3C traceparent carried to Kafka so consumers join the request trace; null outside a trace. */
    @Column("traceparent")
    private String traceParent;

    @Column("schema_version")
    private Integer schemaVersion = 1;

    @Column("publicado_en")
    private OffsetDateTime publicadoEn;

    @Column("lease_until")
    private OffsetDateTime leaseUntil;

    @Column("lease_owner")
    private String leaseOwner;

    // Failure tracking (ADR-03 gap resolutions)
    @Column("intentos")
    private Integer intentos = 0;

    @Column("primer_fallo_en")
    private OffsetDateTime primerFalloEn;

    @Column("ultimo_fallo_en")
    private OffsetDateTime ultimoFalloEn;

    @Column("ultimo_error")
    private String ultimoError;

    @Column("requiere_revision")
    private Boolean requiereRevision = false;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    public OutboxEvento() {}

    public OutboxEvento(UUID eventId, String eventType, String payload, UUID pacienteId,
                        Integer pacienteSequence, OffsetDateTime occurredAt, UUID correlationId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.pacienteId = pacienteId;
        this.pacienteSequence = pacienteSequence;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    // Getters and setters
    @Override
    public UUID getId() { return eventId; }

    @Override
    public boolean isNew() { return isNew; }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public UUID getPacienteId() { return pacienteId; }
    public void setPacienteId(UUID pacienteId) { this.pacienteId = pacienteId; }

    public Integer getPacienteSequence() { return pacienteSequence; }
    public void setPacienteSequence(Integer pacienteSequence) { this.pacienteSequence = pacienteSequence; }

    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }

    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID correlationId) { this.correlationId = correlationId; }

    public String getTraceParent() { return traceParent; }
    public void setTraceParent(String traceParent) { this.traceParent = traceParent; }

    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }

    public OffsetDateTime getPublicadoEn() { return publicadoEn; }
    public void setPublicadoEn(OffsetDateTime publicadoEn) { this.publicadoEn = publicadoEn; }

    public OffsetDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(OffsetDateTime leaseUntil) { this.leaseUntil = leaseUntil; }

    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }

    public Integer getIntentos() { return intentos; }
    public void setIntentos(Integer intentos) { this.intentos = intentos; }

    public OffsetDateTime getPrimerFalloEn() { return primerFalloEn; }
    public void setPrimerFalloEn(OffsetDateTime primerFalloEn) { this.primerFalloEn = primerFalloEn; }

    public OffsetDateTime getUltimoFalloEn() { return ultimoFalloEn; }
    public void setUltimoFalloEn(OffsetDateTime ultimoFalloEn) { this.ultimoFalloEn = ultimoFalloEn; }

    public String getUltimoError() { return ultimoError; }
    public void setUltimoError(String ultimoError) { this.ultimoError = ultimoError; }

    public Boolean getRequiereRevision() { return requiereRevision; }
    public void setRequiereRevision(Boolean requiereRevision) { this.requiereRevision = requiereRevision; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isPublished() {
        return publicadoEn != null;
    }

    public boolean isUnpublished() {
        return publicadoEn == null;
    }
}
