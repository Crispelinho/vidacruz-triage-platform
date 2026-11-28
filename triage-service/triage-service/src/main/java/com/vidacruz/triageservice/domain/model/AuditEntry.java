package com.vidacruz.triageservice.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Append-only RN-04 audit record, independent of the persistence mapping. */
public record AuditEntry(
		UUID id,
		UUID patientId,
		Instant createdAt,
		String previousStatus,
		String nextStatus,
		String eventType) {
}
