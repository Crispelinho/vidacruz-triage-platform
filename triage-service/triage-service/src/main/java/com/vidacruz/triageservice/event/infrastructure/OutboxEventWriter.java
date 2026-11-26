package com.vidacruz.triageservice.event.infrastructure;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.application.filter.CorrelationId;
import com.vidacruz.triageservice.event.domain.OutboxEvento;

import reactor.core.publisher.Mono;

/** Appends events with contiguous per-patient sequence numbers and the published flat envelope (DEC-03, G-T25). */
@Component
public class OutboxEventWriter {

	private static final int SCHEMA_VERSION = 1;

	private final OutboxEventoRepository repository;
	private final ObjectMapper objectMapper;

	public OutboxEventWriter(OutboxEventoRepository repository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.objectMapper = objectMapper;
	}

	public Mono<OutboxEvento> write(UUID patientId, String eventType, Map<String, Object> payload,
			Instant occurredAt) {
		UUID eventId = UUID.randomUUID();
		UUID fallbackCorrelationId = UUID.randomUUID();
		return CorrelationId.current()
				.flatMap(current -> repository.findFirstByPacienteIdOrderByPacienteSequenceDesc(patientId)
						.map(last -> last.getPacienteSequence() + 1)
						.defaultIfEmpty(1)
						.flatMap(sequence -> {
							UUID correlationId = correlationIdFrom(current, fallbackCorrelationId);
							OutboxEvento event = new OutboxEvento(
									eventId,
									eventType,
									null,
									patientId,
									sequence,
									OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC),
									correlationId);
							// The envelope is flattened beside the payload so existing consumers can order and
							// deduplicate without a schemaVersion bump (DEC-03).
							Map<String, Object> body = new LinkedHashMap<>(payload);
							body.put("eventType", eventType);
							body.put("eventId", eventId.toString());
							body.put("schemaVersion", SCHEMA_VERSION);
							body.put("pacienteId", patientId.toString());
							body.put("pacienteSequence", sequence);
							body.put("occurredAt", occurredAt.toString());
							body.put("correlationId", correlationId.toString());
							event.setPayload(serialize(body));
							return repository.save(event);
						}));
	}

	private static UUID correlationIdFrom(String current, UUID fallback) {
		if (current == null || current.isBlank()) {
			return fallback;
		}
		try {
			return UUID.fromString(current);
		} catch (IllegalArgumentException exception) {
			return UUID.nameUUIDFromBytes(current.getBytes(StandardCharsets.UTF_8));
		}
	}

	private String serialize(Map<String, Object> body) {
		try {
			return objectMapper.writeValueAsString(body);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("No se pudo serializar el evento de triaje", exception);
		}
	}
}
