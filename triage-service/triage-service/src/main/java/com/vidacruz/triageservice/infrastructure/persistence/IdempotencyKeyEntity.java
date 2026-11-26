package com.vidacruz.triageservice.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** DEC-15: stored canonical digest and original API-01 response for an idempotency key. */
@Table("idempotency_key")
public record IdempotencyKeyEntity(
		@Id @Column("key") String key,
		@Column("request_hash") String requestHash,
		@Column("paciente_id") UUID patientId,
		@Column("response_status") Integer responseStatus,
		@Column("response_body") String responseBody,
		@Column("created_at") Instant createdAt,
		@Column("expires_at") Instant expiresAt) {
}
