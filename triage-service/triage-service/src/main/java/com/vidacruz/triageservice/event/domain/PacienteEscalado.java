package com.vidacruz.triageservice.event.domain;

import java.time.ZonedDateTime;
import java.util.UUID;

public class PacienteEscalado extends TriageEvent {

    private final String nivel;
    private final int slaMinutos;
    private final ZonedDateTime fechaEscalamiento;

    public PacienteEscalado(
            UUID eventId,
            int schemaVersion,
            UUID pacienteId,
            long pacienteSequence,
            ZonedDateTime occurredAt,
            String correlationId,
            String nivel,
            int slaMinutos,
            ZonedDateTime fechaEscalamiento) {

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

    public String getNivel() {
        return nivel;
    }

    public int getSlaMinutos() {
        return slaMinutos;
    }

    public ZonedDateTime getFechaEscalamiento() {
        return fechaEscalamiento;
    }

    @Override
    public Object getPayload() {
        return new PayloadData(nivel, slaMinutos, fechaEscalamiento);
    }

    private record PayloadData(String nivel, int slaMinutos, ZonedDateTime fechaEscalamiento) {}
}
