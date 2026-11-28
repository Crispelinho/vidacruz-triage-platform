package com.vidacruz.triageservice.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.vidacruz.triageservice.domain.model.IdempotencyKey;
import com.vidacruz.triageservice.domain.port.IdempotencyStore;

import reactor.core.publisher.Mono;

/** R2DBC implementation of DEC-15 key reservation, replay and completion. */
@Component
public class IdempotencyStoreAdapter implements IdempotencyStore {

	private final IdempotencyKeyRepository repository;

	public IdempotencyStoreAdapter(IdempotencyKeyRepository repository) {
		this.repository = repository;
	}

	@Override
	public Mono<IdempotencyKey> findById(String key) {
		return repository.findById(key).map(IdempotencyStoreAdapter::toDomain);
	}

	@Override
	public Mono<IdempotencyKey> findByIdForUpdate(String key) {
		return repository.findByIdForUpdate(key).map(IdempotencyStoreAdapter::toDomain);
	}

	@Override
	public Mono<Integer> insertPending(String key, String requestHash, Instant createdAt, Instant expiresAt) {
		return repository.insertPending(key, requestHash, createdAt, expiresAt);
	}

	@Override
	public Mono<Integer> complete(String key, UUID patientId, int responseStatus, String responseBody) {
		return repository.complete(key, patientId, responseStatus, responseBody);
	}

	private static IdempotencyKey toDomain(IdempotencyKeyEntity entity) {
		return new IdempotencyKey(
				entity.key(), entity.requestHash(), entity.patientId(), entity.responseStatus(),
				entity.responseBody(), entity.createdAt(), entity.expiresAt());
	}
}
