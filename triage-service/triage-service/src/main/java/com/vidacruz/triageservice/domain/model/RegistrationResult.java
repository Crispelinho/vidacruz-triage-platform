package com.vidacruz.triageservice.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Outcome of RF-01: the classification the API returns and an idempotent replay must reproduce (DEC-15). */
public record RegistrationResult(UUID id, TriageLevel level, int slaMinutes, String status, Instant registeredAt) {
}
