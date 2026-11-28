package com.vidacruz.triageservice.event.infrastructure.contract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.ZonedDateTime;
import java.util.UUID;

public class PacienteAlta extends TriageEvent {

    private final String estadoAnterior;
    private final ZonedDateTime fechaAlta;

    @JsonCreator
    public PacienteAlta(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("pacienteId") UUID pacienteId,
            @JsonProperty("pacienteSequence") long pacienteSequence,
            @JsonProperty("occurredAt") ZonedDateTime occurredAt,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("estadoAnterior") String estadoAnterior,
            @JsonProperty("fechaAlta") ZonedDateTime fechaAlta) {

        super(eventId, EventType.EVT_04_PACIENTE_ALTA, schemaVersion, pacienteId,
              pacienteSequence, occurredAt, correlationId);

        if (estadoAnterior == null || estadoAnterior.isBlank()) {
            throw new IllegalArgumentException("estadoAnterior cannot be null or blank");
        }
        if (fechaAlta == null) {
            throw new IllegalArgumentException("fechaAlta cannot be null");
        }

        this.estadoAnterior = estadoAnterior;
        this.fechaAlta = fechaAlta;
    }

    @JsonProperty("estadoAnterior")
    public String getEstadoAnterior() {
        return estadoAnterior;
    }

    @JsonProperty("fechaAlta")
    public ZonedDateTime getFechaAlta() {
        return fechaAlta;
    }

    @Override
    public Object getPayload() {
        return new PayloadData(estadoAnterior, fechaAlta);
    }

    private record PayloadData(String estadoAnterior, ZonedDateTime fechaAlta) {}
}
