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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Map.entry;

/**
 * Reads the outbox payload published by the triage service on {@code triaje.eventos}. Envelope fields may be flat at
 * the root or nested under {@code payload}; the Kafka record key is a fallback for older messages.
 */
final class TriageEventParser {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, PatientStatus> PATIENT_EVENTS = Map.ofEntries(
            entry("paciente_registrado", PatientStatus.REGISTRADO),
            entry("paciente_escalado", PatientStatus.REGISTRADO),
            entry("paciente_en_consulta", PatientStatus.EN_CONSULTA),
            entry("paciente_alta", PatientStatus.ALTA),
            entry("EVT_01_PACIENTE_REGISTRADO", PatientStatus.REGISTRADO),
            entry("EVT_02_PACIENTE_ESCALADO", PatientStatus.REGISTRADO),
            entry("EVT_03_PACIENTE_EN_CONSULTA", PatientStatus.EN_CONSULTA),
            entry("EVT_04_PACIENTE_ALTA", PatientStatus.ALTA));

    private TriageEventParser() {
    }

    /** Returns the snapshot, or empty for an event type the projection does not use. */
    static Optional<PatientSnapshot> parse(String message, String recordKey) {
        try {
            var event = JSON.readTree(message == null ? "" : message);
            var status = event == null ? null : PATIENT_EVENTS.get(field(event, "eventType").asText());
            if (status == null) {
                return Optional.empty();
            }
            var patientId = text(event, "pacienteId", false)
                    .map(UUID::fromString)
                    .orElseGet(() -> UUID.fromString(requireKey(recordKey)));
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

    /** Flat envelope fields win over the legacy nested payload when both are present. */
    private static JsonNode field(JsonNode event, String name) {
        var value = event.path(name);
        return value.isMissingNode() || value.isNull() ? event.path("payload").path(name) : value;
    }

    private static Optional<String> text(JsonNode event, String name, boolean required) {
        var value = field(event, name);
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
        var value = field(event, "pacienteSequence");
        if (!value.isNumber() || value.asLong() < 1) {
            throw new IllegalArgumentException("The event has no valid pacienteSequence");
        }
        return value.asLong();
    }
}
