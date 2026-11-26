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

/** Appends events with contiguous per-patient sequence numbers and the published envelope (DEC-03, G-T25). */
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
		UUID correlationId = UUID.randomUUID();
		return CorrelationId.current()
				.flatMap(current -> repository.findFirstByPacienteIdOrderByPacienteSequenceDesc(patientId)
						.map(last -> last.getPacienteSequence() + 1)
						.defaultIfEmpty(1)
						.flatMap(sequence -> {
							OutboxEvento event = new OutboxEvento(
									eventId,
									eventType,
									envelope(eventId, eventType, patientId, sequence, occurredAt,
											correlationIdFrom(current, correlationId), payload),
									patientId,
									sequence,
									OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC),
									correlationIdFrom(current, correlationId));
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

	private String envelope(UUID eventId, String eventType, UUID patientId, int sequence, Instant occurredAt,
			UUID correlationId, Map<String, Object> payload) {
		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("eventId", eventId.toString());
		envelope.put("eventType", eventType);
		envelope.put("schemaVersion", SCHEMA_VERSION);
		envelope.put("pacienteId", patientId.toString());
		envelope.put("pacienteSequence", sequence);
		envelope.put("occurredAt", occurredAt.toString());
		envelope.put("correlationId", correlationId.toString());
		envelope.put("payload", payload);
		return serialize(envelope);
	}

	private String serialize(Map<String, Object> body) {
		try {
			return objectMapper.writeValueAsString(body);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("No se pudo serializar el evento de triaje", exception);
		}
	}
}
