package com.vidacruz.triageservice.event.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.ZonedDateTime;
import java.util.UUID;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "eventType",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PacienteRegistrado.class, name = "EVT_01_PACIENTE_REGISTRADO"),
    @JsonSubTypes.Type(value = PacienteEscalado.class, name = "EVT_02_PACIENTE_ESCALADO"),
    @JsonSubTypes.Type(value = PacienteEnConsulta.class, name = "EVT_03_PACIENTE_EN_CONSULTA"),
    @JsonSubTypes.Type(value = PacienteAlta.class, name = "EVT_04_PACIENTE_ALTA")
})
public abstract class TriageEvent {

    private final UUID eventId;
    private final EventType eventType;
    private final int schemaVersion;
    private final UUID pacienteId;
    private final long pacienteSequence;
    private final ZonedDateTime occurredAt;
    private final String correlationId;

    @JsonCreator
    protected TriageEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("eventType") EventType eventType,
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("pacienteId") UUID pacienteId,
            @JsonProperty("pacienteSequence") long pacienteSequence,
            @JsonProperty("occurredAt") ZonedDateTime occurredAt,
            @JsonProperty("correlationId") String correlationId) {

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

    @JsonProperty("eventId")
    public UUID getEventId() {
        return eventId;
    }

    @JsonProperty("eventType")
    public EventType getEventType() {
        return eventType;
    }

    @JsonProperty("schemaVersion")
    public int getSchemaVersion() {
        return schemaVersion;
    }

    @JsonProperty("pacienteId")
    public UUID getPacienteId() {
        return pacienteId;
    }

    @JsonProperty("pacienteSequence")
    public long getPacienteSequence() {
        return pacienteSequence;
    }

    @JsonProperty("occurredAt")
    public ZonedDateTime getOccurredAt() {
        return occurredAt;
    }

    @JsonProperty("correlationId")
    public String getCorrelationId() {
        return correlationId;
    }

    @JsonIgnore
    public abstract Object getPayload();
}
