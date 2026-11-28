package com.vidacruz.triageservice.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.domain.model.IdempotencyRecord;
import com.vidacruz.triageservice.domain.model.IdempotencyStore;
import com.vidacruz.triageservice.domain.model.RegistrationResult;
import com.vidacruz.triageservice.domain.model.TriageLevel;

import reactor.core.publisher.Mono;

/**
 * R2DBC adapter for DEC-15 idempotency keys. The stored body keeps the API-01 wire format, so keys written before
 * this adapter existed still replay.
 */
@Repository
public class R2dbcIdempotencyStore implements IdempotencyStore {

	private static final int CREATED = 201;

	private final IdempotencyKeyRepository repository;
	private final ObjectMapper objectMapper;

	public R2dbcIdempotencyStore(IdempotencyKeyRepository repository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.objectMapper = objectMapper;
	}

	@Override
	public Mono<IdempotencyRecord> find(String key) {
		return repository.findById(key).map(this::toRecord);
	}

	@Override
	public Mono<Boolean> reserve(String key, String requestHash, Instant createdAt, Instant expiresAt) {
		return repository.insertPending(key, requestHash, createdAt, expiresAt).map(inserted -> inserted == 1);
	}

	@Override
	public Mono<IdempotencyRecord> findForUpdate(String key) {
		return repository.findByIdForUpdate(key).map(this::toRecord);
	}

	@Override
	public Mono<Void> complete(String key, RegistrationResult response) {
		return Mono.fromCallable(() -> serialize(response))
				.flatMap(body -> repository.complete(key, response.id(), CREATED, body))
				.then();
	}

	private IdempotencyRecord toRecord(IdempotencyKeyEntity stored) {
		return new IdempotencyRecord(stored.key(), stored.requestHash(), deserialize(stored.responseBody()));
	}

	private String serialize(RegistrationResult response) {
		try {
			return objectMapper.writeValueAsString(new StoredResponse(response.id(), response.level().name(),
					response.slaMinutes(), response.status(), response.registeredAt()));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Cannot store idempotency response", exception);
		}
	}

	private RegistrationResult deserialize(String body) {
		if (body == null) {
			throw new IllegalStateException("Stored idempotency response is not readable");
		}
		try {
			StoredResponse stored = objectMapper.readValue(body, StoredResponse.class);
			return new RegistrationResult(stored.id(), TriageLevel.valueOf(stored.level()), stored.slaMinutes(),
					stored.status(), stored.registeredAt());
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Stored idempotency response is not readable", exception);
		}
	}

	/** API-01 response body exactly as earlier versions stored it. */
	record StoredResponse(
			@JsonProperty("id") UUID id,
			@JsonProperty("nivel") String level,
			@JsonProperty("slaMinutos") int slaMinutes,
			@JsonProperty("estado") String status,
			@JsonProperty("fechaRegistro") Instant registeredAt) {
	}
}
