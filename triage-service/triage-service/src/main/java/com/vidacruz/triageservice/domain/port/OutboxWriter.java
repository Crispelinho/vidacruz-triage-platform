package com.vidacruz.triageservice.domain.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import reactor.core.publisher.Mono;

/** Outbox append boundary used by clinical use cases. */
public interface OutboxWriter {

	Mono<Void> write(UUID patientId, String eventType, Map<String, Object> payload, Instant occurredAt);
}
