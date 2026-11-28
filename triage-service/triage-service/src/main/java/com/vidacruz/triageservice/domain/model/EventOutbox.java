package com.vidacruz.triageservice.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import reactor.core.publisher.Mono;

/**
 * Transactional outbox port (ADR-03): the event row is written in the caller's transaction, so it exists if and only
 * if the state change commits. Publication to Kafka happens later and is not the caller's concern.
 */
public interface EventOutbox {

	Mono<Void> append(UUID patientId, String eventType, Map<String, Object> payload, Instant occurredAt);
}
