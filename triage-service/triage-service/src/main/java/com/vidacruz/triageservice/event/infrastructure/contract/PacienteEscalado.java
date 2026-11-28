package com.vidacruz.triageservice.event.infrastructure.contract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.ZonedDateTime;
import java.util.UUID;

public class PacienteEscalado extends TriageEvent {

    private final String nivel;
    private final int slaMinutos;
    private final ZonedDateTime fechaEscalamiento;

    @JsonCreator
    public PacienteEscalado(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("pacienteId") UUID pacienteId,
            @JsonProperty("pacienteSequence") long pacienteSequence,
            @JsonProperty("occurredAt") ZonedDateTime occurredAt,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("nivel") String nivel,
            @JsonProperty("slaMinutos") int slaMinutos,
            @JsonProperty("fechaEscalamiento") ZonedDateTime fechaEscalamiento) {

        super(eventId, EventType.EVT_02_PACIENTE_ESCALADO, schemaVersion, pacienteId,
              pacienteSequence, occurredAt, correlationId);

        if (nivel == null || nivel.isBlank()) {
            throw new IllegalArgumentException("nivel cannot be null or blank");
        }
        if (fechaEscalamiento == null) {
            throw new IllegalArgumentException("fechaEscalamiento cannot be null");
        }

        this.nivel = nivel;
        this.slaMinutos = slaMinutos;
        this.fechaEscalamiento = fechaEscalamiento;
    }

    @JsonProperty("nivel")
    public String getNivel() {
        return nivel;
    }

    @JsonProperty("slaMinutos")
    public int getSlaMinutos() {
        return slaMinutos;
    }

    @JsonProperty("fechaEscalamiento")
    public ZonedDateTime getFechaEscalamiento() {
        return fechaEscalamiento;
    }

    @Override
    public Object getPayload() {
        return new PayloadData(nivel, slaMinutos, fechaEscalamiento);
    }

    private record PayloadData(String nivel, int slaMinutos, ZonedDateTime fechaEscalamiento) {}
}
