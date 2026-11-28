package com.vidacruz.triageservice.domain.port;

import java.time.Instant;
import java.util.UUID;

import com.vidacruz.triageservice.domain.model.IdempotencyKey;

import reactor.core.publisher.Mono;

/** DEC-15 key reservation, replay and completion boundary. */
public interface IdempotencyStore {

	Mono<IdempotencyKey> findById(String key);

	Mono<IdempotencyKey> findByIdForUpdate(String key);

	Mono<Integer> insertPending(String key, String requestHash, Instant createdAt, Instant expiresAt);

	Mono<Integer> complete(String key, UUID patientId, int responseStatus, String responseBody);
}
