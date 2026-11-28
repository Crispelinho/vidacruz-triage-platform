package com.vidacruz.triageservice.event.infrastructure.contract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.ZonedDateTime;
import java.util.UUID;

public class PacienteRegistrado extends TriageEvent {

    private final String nivel;
    private final int slaMinutos;
    private final String estado;
    private final ZonedDateTime fechaRegistro;

    @JsonCreator
    public PacienteRegistrado(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("pacienteId") UUID pacienteId,
            @JsonProperty("pacienteSequence") long pacienteSequence,
            @JsonProperty("occurredAt") ZonedDateTime occurredAt,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("nivel") String nivel,
            @JsonProperty("slaMinutos") int slaMinutos,
            @JsonProperty("estado") String estado,
            @JsonProperty("fechaRegistro") ZonedDateTime fechaRegistro) {

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

    @JsonProperty("nivel")
    public String getNivel() {
        return nivel;
    }

    @JsonProperty("slaMinutos")
    public int getSlaMinutos() {
        return slaMinutos;
    }

    @JsonProperty("estado")
    public String getEstado() {
        return estado;
    }

    @JsonProperty("fechaRegistro")
    public ZonedDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    @Override
    public Object getPayload() {
        return new PayloadData(nivel, slaMinutos, estado, fechaRegistro);
    }

    private record PayloadData(String nivel, int slaMinutos, String estado, ZonedDateTime fechaRegistro) {}
}
