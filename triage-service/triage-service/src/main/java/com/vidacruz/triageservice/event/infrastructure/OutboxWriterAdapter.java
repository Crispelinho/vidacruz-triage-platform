package com.vidacruz.triageservice.event.infrastructure;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.vidacruz.triageservice.domain.port.OutboxWriter;

import reactor.core.publisher.Mono;

/** Adapts the existing outbox writer to the clinical domain boundary. */
@Component
public class OutboxWriterAdapter implements OutboxWriter {

	private final OutboxEventWriter writer;

	public OutboxWriterAdapter(OutboxEventWriter writer) {
		this.writer = writer;
	}

	@Override
	public Mono<Void> write(UUID patientId, String eventType, Map<String, Object> payload, Instant occurredAt) {
		return writer.write(patientId, eventType, payload, occurredAt).then();
	}
}
