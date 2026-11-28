package com.vidacruz.triageservice.domain.model;

import java.time.Instant;

import reactor.core.publisher.Mono;

/** Storage port for DEC-15 idempotency keys. */
public interface IdempotencyStore {

	Mono<IdempotencyRecord> find(String key);

	/** Reserves the key; {@code false} when another request already owns it (DEC-15 step 2). */
	Mono<Boolean> reserve(String key, String requestHash, Instant createdAt, Instant expiresAt);

	/** Locks the key so a concurrent replay observes the winner's committed response. */
	Mono<IdempotencyRecord> findForUpdate(String key);

	Mono<Void> complete(String key, RegistrationResult response);
}
