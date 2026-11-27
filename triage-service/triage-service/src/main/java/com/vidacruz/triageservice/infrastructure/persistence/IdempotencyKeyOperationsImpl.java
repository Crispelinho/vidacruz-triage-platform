package com.vidacruz.triageservice.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Mono;

/** DatabaseClient implementation of the idempotency-key write fragment. */
@Repository
public class IdempotencyKeyOperationsImpl implements IdempotencyKeyOperations {

	private final DatabaseClient databaseClient;

	public IdempotencyKeyOperationsImpl(DatabaseClient databaseClient) {
		this.databaseClient = databaseClient;
	}

	@Override
	public Mono<Integer> insertPending(String key, String requestHash, Instant createdAt, Instant expiresAt) {
		return databaseClient.sql("""
				INSERT INTO idempotency_key (key, request_hash, created_at, expires_at)
				VALUES (:key, :requestHash, :createdAt, :expiresAt)
				ON CONFLICT (key) DO NOTHING
				""")
				.bind("key", key)
				.bind("requestHash", requestHash)
				.bind("createdAt", createdAt)
				.bind("expiresAt", expiresAt)
				.fetch().rowsUpdated().map(rows -> rows.intValue());
	}

	@Override
	public Mono<IdempotencyKeyEntity> findByIdForUpdate(String key) {
		return databaseClient.sql("""
				SELECT key, request_hash, paciente_id, response_status,
				       response_body::text AS response_body, created_at, expires_at
				FROM idempotency_key WHERE key = :key FOR UPDATE
				""")
				.bind("key", key)
				.map((row, metadata) -> new IdempotencyKeyEntity(
						row.get("key", String.class),
						trim(row.get("request_hash", String.class)),
						row.get("paciente_id", UUID.class),
						integer(row.get("response_status", Object.class)),
						row.get("response_body", String.class),
						row.get("created_at", Instant.class),
						row.get("expires_at", Instant.class)))
				.one();
	}

	@Override
	public Mono<Integer> complete(String key, UUID patientId, int responseStatus, String responseBody) {
		DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
				UPDATE idempotency_key
				SET paciente_id = :patientId, response_status = :responseStatus,
				    response_body = CAST(:responseBody AS jsonb)
				WHERE key = :key
				""")
				.bind("patientId", patientId)
				.bind("responseStatus", responseStatus)
				.bind("responseBody", responseBody)
				.bind("key", key);
		return spec.fetch().rowsUpdated().map(rows -> rows.intValue());
	}

	@Override
	public Mono<Integer> deleteExpired(Instant now) {
		return databaseClient.sql("DELETE FROM idempotency_key WHERE expires_at < :now")
				.bind("now", now)
				.fetch().rowsUpdated().map(rows -> rows.intValue());
	}

	private static String trim(String value) {
		return value == null ? null : value.trim();
	}

	private static Integer integer(Object value) {
		return value == null ? null : ((Number) value).intValue();
	}
}
