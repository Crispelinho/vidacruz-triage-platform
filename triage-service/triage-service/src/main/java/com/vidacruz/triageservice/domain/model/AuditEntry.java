package com.vidacruz.triageservice.domain.model;

import java.time.Instant;
import java.util.UUID;

/** RN-04 audit row: one per registration, state change or escalation. */
public record AuditEntry(UUID id, UUID patientId, Instant createdAt, String previousStatus, String nextStatus,
		String eventType) {
}
