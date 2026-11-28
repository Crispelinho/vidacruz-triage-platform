package com.vidacruz.triageservice.event.domain;

import java.time.ZonedDateTime;
import java.util.UUID;

public class PacienteEnConsulta extends TriageEvent {

    private final String estadoAnterior;
    private final ZonedDateTime fechaEntradaConsulta;

    public PacienteEnConsulta(
            UUID eventId,
            int schemaVersion,
            UUID pacienteId,
            long pacienteSequence,
            ZonedDateTime occurredAt,
            String correlationId,
            String estadoAnterior,
            ZonedDateTime fechaEntradaConsulta) {

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

    public String getEstadoAnterior() {
        return estadoAnterior;
    }

    public ZonedDateTime getFechaEntradaConsulta() {
        return fechaEntradaConsulta;
    }

    @Override
    public Object getPayload() {
        return new PayloadData(estadoAnterior, fechaEntradaConsulta);
    }

    private record PayloadData(String estadoAnterior, ZonedDateTime fechaEntradaConsulta) {}
}
