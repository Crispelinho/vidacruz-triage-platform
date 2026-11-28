package com.vidacruz.triageservice.event.infrastructure.contract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.ZonedDateTime;
import java.util.UUID;

public class PacienteEnConsulta extends TriageEvent {

    private final String estadoAnterior;
    private final ZonedDateTime fechaEntradaConsulta;

    @JsonCreator
    public PacienteEnConsulta(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("pacienteId") UUID pacienteId,
            @JsonProperty("pacienteSequence") long pacienteSequence,
            @JsonProperty("occurredAt") ZonedDateTime occurredAt,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("estadoAnterior") String estadoAnterior,
            @JsonProperty("fechaEntradaConsulta") ZonedDateTime fechaEntradaConsulta) {

        super(eventId, EventType.EVT_03_PACIENTE_EN_CONSULTA, schemaVersion, pacienteId,
              pacienteSequence, occurredAt, correlationId);

        if (estadoAnterior == null || estadoAnterior.isBlank()) {
            throw new IllegalArgumentException("estadoAnterior cannot be null or blank");
        }
        if (fechaEntradaConsulta == null) {
            throw new IllegalArgumentException("fechaEntradaConsulta cannot be null");
        }

        this.estadoAnterior = estadoAnterior;
        this.fechaEntradaConsulta = fechaEntradaConsulta;
    }

    @JsonProperty("estadoAnterior")
    public String getEstadoAnterior() {
        return estadoAnterior;
    }

    @JsonProperty("fechaEntradaConsulta")
    public ZonedDateTime getFechaEntradaConsulta() {
        return fechaEntradaConsulta;
    }

    @Override
    public Object getPayload() {
        return new PayloadData(estadoAnterior, fechaEntradaConsulta);
    }

    private record PayloadData(String estadoAnterior, ZonedDateTime fechaEntradaConsulta) {}
}
