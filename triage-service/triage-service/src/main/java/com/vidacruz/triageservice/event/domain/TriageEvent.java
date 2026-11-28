package com.vidacruz.triageservice.event.domain;

import java.time.ZonedDateTime;
import java.util.UUID;

public abstract class TriageEvent {

    private final UUID eventId;
    private final EventType eventType;
    private final int schemaVersion;
    private final UUID pacienteId;
    private final long pacienteSequence;
    private final ZonedDateTime occurredAt;
    private final String correlationId;

    protected TriageEvent(
            UUID eventId,
            EventType eventType,
            int schemaVersion,
            UUID pacienteId,
            long pacienteSequence,
            ZonedDateTime occurredAt,
            String correlationId) {

        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("eventType cannot be null");
        }
        if (pacienteId == null) {
            throw new IllegalArgumentException("pacienteId cannot be null");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt cannot be null");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId cannot be null or blank");
        }

        this.eventId = eventId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.pacienteId = pacienteId;
        this.pacienteSequence = pacienteSequence;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public UUID getPacienteId() {
        return pacienteId;
    }

    public long getPacienteSequence() {
        return pacienteSequence;
    }

    public ZonedDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public abstract Object getPayload();
}
