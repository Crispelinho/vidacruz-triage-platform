package com.vidacruz.triageservice.event.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.event.domain.OutboxEvento;

import reactor.core.publisher.Mono;

/** Appends events with contiguous per-patient sequence numbers (DEC-03). */
@Component
public class OutboxEventWriter {

	private final OutboxEventoRepository repository;
	private final ObjectMapper objectMapper;

	public OutboxEventWriter(OutboxEventoRepository repository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.objectMapper = objectMapper;
	}

	public Mono<OutboxEvento> write(UUID patientId, String eventType, Map<String, Object> payload,
			Instant occurredAt) {
		return repository.findFirstByPacienteIdOrderByPacienteSequenceDesc(patientId)
				.map(last -> last.getPacienteSequence() + 1)
				.defaultIfEmpty(1)
				.flatMap(sequence -> {
					OutboxEvento event = new OutboxEvento(
							UUID.randomUUID(),
							eventType,
							null,
							patientId,
							sequence,
							OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC),
							UUID.randomUUID());
					// The published payload carries the envelope so consumers can order and deduplicate events
					// without querying the triage database (DEC-03).
					Map<String, Object> body = new LinkedHashMap<>(payload);
					body.put("eventType", eventType);
					body.put("eventId", event.getEventId().toString());
					body.put("schemaVersion", event.getSchemaVersion());
					body.put("pacienteId", event.getPacienteId().toString());
					body.put("pacienteSequence", event.getPacienteSequence());
					body.put("occurredAt", event.getOccurredAt().toInstant().toString());
					body.put("correlationId", event.getCorrelationId().toString());
					event.setPayload(serialize(body));
					return repository.save(event);
				});
	}

	private String serialize(Map<String, Object> body) {
		try {
			return objectMapper.writeValueAsString(body);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("No se pudo serializar el evento de triaje", exception);
		}
	}
}
