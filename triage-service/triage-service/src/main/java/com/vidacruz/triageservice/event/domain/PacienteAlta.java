package com.vidacruz.triageservice.event.domain;

import java.time.ZonedDateTime;
import java.util.UUID;

public class PacienteAlta extends TriageEvent {

    private final String estadoAnterior;
    private final ZonedDateTime fechaAlta;

    public PacienteAlta(
            UUID eventId,
            int schemaVersion,
            UUID pacienteId,
            long pacienteSequence,
            ZonedDateTime occurredAt,
            String correlationId,
            String estadoAnterior,
            ZonedDateTime fechaAlta) {

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

    public String getEstadoAnterior() {
        return estadoAnterior;
    }

    public ZonedDateTime getFechaAlta() {
        return fechaAlta;
    }

    @Override
    public Object getPayload() {
        return new PayloadData(estadoAnterior, fechaAlta);
    }

    private record PayloadData(String estadoAnterior, ZonedDateTime fechaAlta) {}
}
