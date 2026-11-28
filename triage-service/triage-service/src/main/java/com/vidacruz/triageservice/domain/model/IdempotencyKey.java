package com.vidacruz.triageservice.domain.model;

import java.time.Instant;
import java.util.UUID;

/** DEC-15 stored digest and original response, without Spring Data mapping annotations. */
public record IdempotencyKey(
		String key,
		String requestHash,
		UUID patientId,
		Integer responseStatus,
		String responseBody,
		Instant createdAt,
		Instant expiresAt) {
}
