package com.vidacruz.clinical.domain.model.urgency;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * State of a patient as published by one triage event. Consultation and discharge events do not carry the level: the
 * projection keeps the last known one. The sequence comes from the outbox and orders events of the same patient.
 *
 * @param patientId  patient
 * @param level      triage level, empty when the event does not carry it
 * @param status     clinical status after the event
 * @param occurredAt when the change happened in the triage
 * @param sequence   contiguous per-patient sequence of the outbox event
 */
public record PatientSnapshot(UUID patientId, Optional<TriageLevel> level, PatientStatus status,
                              Instant occurredAt, long sequence) {

    public PatientSnapshot {
        Objects.requireNonNull(patientId, "patientId");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
    }

    public boolean isActive() {
        return status.isActive();
    }
}
