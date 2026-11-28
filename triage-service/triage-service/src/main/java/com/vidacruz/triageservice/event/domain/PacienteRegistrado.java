package com.vidacruz.triageservice.event.domain;

import java.time.ZonedDateTime;
import java.util.UUID;

public class PacienteRegistrado extends TriageEvent {

    private final String nivel;
    private final int slaMinutos;
    private final String estado;
    private final ZonedDateTime fechaRegistro;

    public PacienteRegistrado(
            UUID eventId,
            int schemaVersion,
            UUID pacienteId,
            long pacienteSequence,
            ZonedDateTime occurredAt,
            String correlationId,
            String nivel,
            int slaMinutos,
            String estado,
            ZonedDateTime fechaRegistro) {

        super(eventId, EventType.EVT_01_PACIENTE_REGISTRADO, schemaVersion, pacienteId,
              pacienteSequence, occurredAt, correlationId);

        if (nivel == null || nivel.isBlank()) {
            throw new IllegalArgumentException("nivel cannot be null or blank");
        }
        if (estado == null || estado.isBlank()) {
            throw new IllegalArgumentException("estado cannot be null or blank");
        }
        if (fechaRegistro == null) {
            throw new IllegalArgumentException("fechaRegistro cannot be null");
        }

        this.nivel = nivel;
        this.slaMinutos = slaMinutos;
        this.estado = estado;
        this.fechaRegistro = fechaRegistro;
    }

    public String getNivel() {
        return nivel;
    }

    public int getSlaMinutos() {
        return slaMinutos;
    }

    public String getEstado() {
        return estado;
    }

    public ZonedDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    @Override
    public Object getPayload() {
        return new PayloadData(nivel, slaMinutos, estado, fechaRegistro);
    }

    private record PayloadData(String nivel, int slaMinutos, String estado, ZonedDateTime fechaRegistro) {}
}
