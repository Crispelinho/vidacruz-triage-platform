package com.vidacruz.triageservice.event.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.application.filter.CorrelationId;
import com.vidacruz.triageservice.event.domain.OutboxEvento;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OutboxEventWriterTest {

	private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

	@Mock
	private OutboxEventoRepository repository;

	private OutboxEventWriter writer;

	@BeforeEach
	void setUp() {
		writer = new OutboxEventWriter(repository, mapper);
		when(repository.findFirstByPacienteIdOrderByPacienteSequenceDesc(any(UUID.class)))
				.thenReturn(Mono.empty());
		when(repository.save(any(OutboxEvento.class)))
				.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
	}

	@Test
	void writesPublishedEnvelopeWithNestedPayloadAndRequestCorrelation() throws Exception {
		UUID patientId = UUID.randomUUID();
		Instant occurredAt = Instant.parse("2026-11-26T16:00:00Z");

		StepVerifier.create(writer.write(patientId, "paciente_registrado",
						Map.of("nivel", "CRITICO"), occurredAt)
						.contextWrite(context -> context.put(CorrelationId.CONTEXT_KEY, "corr-123")))
				.assertNext(event -> {
					assertThat(event.getPacienteSequence()).isEqualTo(1);
					assertThat(event.getEventType()).isEqualTo("paciente_registrado");
				})
				.verifyComplete();

		ArgumentCaptor<OutboxEvento> captor = ArgumentCaptor.forClass(OutboxEvento.class);
		org.mockito.Mockito.verify(repository).save(captor.capture());
		JsonNode envelope = mapper.readTree(captor.getValue().getPayload());
		assertThat(envelope.fieldNames()).toIterable().containsExactly(
				"eventId", "eventType", "schemaVersion", "pacienteId", "pacienteSequence",
				"occurredAt", "correlationId", "payload");
		assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
		assertThat(envelope.get("pacienteId").asText()).isEqualTo(patientId.toString());
		assertThat(envelope.get("pacienteSequence").asInt()).isEqualTo(1);
		assertThat(envelope.get("correlationId").asText())
				.isEqualTo(UUID.nameUUIDFromBytes("corr-123".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
		assertThat(envelope.get("payload").get("nivel").asText()).isEqualTo("CRITICO");
		assertThat(envelope.get("eventType").asText()).isEqualTo("paciente_registrado");
		assertThat(envelope.get("eventId").asText()).isEqualTo(captor.getValue().getEventId().toString());
	}
}
