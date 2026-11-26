package com.vidacruz.triageservice.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.query.Param;

import reactor.core.publisher.Mono;

public interface IdempotencyKeyRepository extends ReactiveCrudRepository<IdempotencyKeyEntity, String> {

	/** Inserts the key first so concurrent requests serialize on the primary key (DEC-15 step 2). */
	@Query("INSERT INTO idempotency_key (key, request_hash, created_at, expires_at) "
			+ "VALUES (:key, :requestHash, :createdAt, :expiresAt)")
	Mono<Integer> insertPending(@Param("key") String key, @Param("requestHash") String requestHash,
			@Param("createdAt") Instant createdAt, @Param("expiresAt") Instant expiresAt);

	@Query("UPDATE idempotency_key SET paciente_id = :patientId, response_status = :responseStatus, "
			+ "response_body = CAST(:responseBody AS jsonb) WHERE key = :key")
	Mono<Integer> complete(@Param("key") String key, @Param("patientId") UUID patientId,
			@Param("responseStatus") int responseStatus, @Param("responseBody") String responseBody);

	@Query("DELETE FROM idempotency_key WHERE expires_at < :now")
	Mono<Integer> deleteExpired(@Param("now") Instant now);
}
