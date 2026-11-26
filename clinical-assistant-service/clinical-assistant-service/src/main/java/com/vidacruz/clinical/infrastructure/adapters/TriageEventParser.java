package com.vidacruz.clinical.infrastructure.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.clinical.domain.model.urgency.PatientSnapshot;
import com.vidacruz.clinical.domain.model.urgency.PatientStatus;
import com.vidacruz.clinical.domain.model.urgency.TriageLevel;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reads the outbox payload published by the triage service on {@code triaje.eventos}. The envelope fields are added by
 * the triage when it publishes the row; the Kafka record key is used as a fallback for older messages.
 */
final class TriageEventParser {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> PATIENT_EVENTS = Set.of(
            "EVT_01_PACIENTE_REGISTRADO",
            "EVT_02_PACIENTE_ESCALADO",
            "EVT_03_PACIENTE_EN_CONSULTA",
            "EVT_04_PACIENTE_ALTA");

    private TriageEventParser() {
    }

    /** Returns the snapshot, or empty for an event type the projection does not use. */
    static Optional<PatientSnapshot> parse(String message, String recordKey) {
        try {
            var event = JSON.readTree(message == null ? "" : message);
            if (event == null || !PATIENT_EVENTS.contains(event.path("eventType").asText())) {
                return Optional.empty();
            }
            var patientId = text(event, "pacienteId", false)
                    .map(UUID::fromString)
                    .orElseGet(() -> UUID.fromString(requireKey(recordKey)));
            var status = status(event.path("eventType").asText());
            var level = text(event, "nivel", status == PatientStatus.REGISTRADO)
                    .map(TriageLevel::fromCode);
            return Optional.of(new PatientSnapshot(
                    patientId,
                    level,
                    status,
                    occurredAt(event),
                    sequence(event)));
        } catch (JsonProcessingException | DateTimeParseException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid triage event: " + exception.getMessage(), exception);
        }
    }

    private static PatientStatus status(String eventType) {
        return switch (eventType) {
            case "EVT_01_PACIENTE_REGISTRADO", "EVT_02_PACIENTE_ESCALADO" -> PatientStatus.REGISTRADO;
            case "EVT_03_PACIENTE_EN_CONSULTA" -> PatientStatus.EN_CONSULTA;
            case "EVT_04_PACIENTE_ALTA" -> PatientStatus.ALTA;
            default -> throw new IllegalArgumentException("Unknown event type " + eventType);
        };
    }

    private static Optional<String> text(JsonNode event, String name, boolean required) {
        var value = event.path(name);
        if (!value.isTextual() || value.asText().isBlank()) {
            if (required) {
                throw new IllegalArgumentException("The event has no " + name);
            }
            return Optional.empty();
        }
        return Optional.of(value.asText());
    }

    private static String requireKey(String recordKey) {
        if (recordKey == null || recordKey.isBlank()) {
            throw new IllegalArgumentException("The event has no pacienteId and the record has no key");
        }
        return recordKey;
    }

    private static Instant occurredAt(JsonNode event) {
        var value = text(event, "occurredAt", true).orElseThrow();
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return OffsetDateTime.parse(value).toInstant();
        }
    }

    private static long sequence(JsonNode event) {
        var value = event.path("pacienteSequence");
        if (!value.isNumber() || value.asLong() < 1) {
            throw new IllegalArgumentException("The event has no valid pacienteSequence");
        }
        return value.asLong();
    }
}
