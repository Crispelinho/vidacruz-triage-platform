package com.vidacruz.triageservice.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import reactor.core.publisher.Mono;

/** Write operations for idempotency keys; implemented with DatabaseClient so counts are reliable. */
public interface IdempotencyKeyOperations {

	/** Reserves the key, returning 0 when another request already owns it (DEC-15 step 2). */
	Mono<Integer> insertPending(String key, String requestHash, Instant createdAt, Instant expiresAt);

	/** Locks the key row so a concurrent replay observes the winner's committed response. */
	Mono<IdempotencyKeyEntity> findByIdForUpdate(String key);

	Mono<Integer> complete(String key, UUID patientId, int responseStatus, String responseBody);

	Mono<Integer> deleteExpired(Instant now);
}
