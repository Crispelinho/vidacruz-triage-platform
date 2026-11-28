package com.vidacruz.triageservice.event.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
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
import com.vidacruz.triageservice.event.infrastructure.OutboxEvento;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OutboxEventWriterTest {

	private static final String TRACEPARENT =
			"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

	private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

	@Mock
	private OutboxEventoRepository repository;

	@Mock
	private Tracer tracer;

	@Mock
	private CurrentTraceContext currentTraceContext;

	@Mock
	private TraceContext traceContext;

	@Mock
	private Propagator propagator;

	private OutboxEventWriter writer;

	@BeforeEach
	void setUp() {
		writer = new OutboxEventWriter(repository, mapper, tracer, propagator);
		when(tracer.currentTraceContext()).thenReturn(currentTraceContext);
		when(repository.findFirstByPacienteIdOrderByPacienteSequenceDesc(any(UUID.class)))
				.thenReturn(Mono.empty());
		when(repository.save(any(OutboxEvento.class)))
				.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
	}

	@Test
	void writesFlatEnvelopeBesidePayloadWithRequestCorrelation() throws Exception {
		UUID patientId = UUID.randomUUID();
		Instant occurredAt = Instant.parse("2026-11-26T16:00:00Z");
		String expectedCorrelation = UUID.nameUUIDFromBytes(
				"corr-123".getBytes(StandardCharsets.UTF_8)).toString();

		StepVerifier.create(writer.write(patientId, "paciente_registrado",
						Map.of("nivel", "CRITICO"), occurredAt)
						.contextWrite(context -> context.put(CorrelationId.CONTEXT_KEY, "corr-123")))
				.assertNext(event -> {
					assertThat(event.getPacienteSequence()).isEqualTo(1);
					assertThat(event.getEventType()).isEqualTo("paciente_registrado");
					assertThat(event.getCorrelationId().toString()).isEqualTo(expectedCorrelation);
					assertThat(event.getTraceParent()).isNull();
				})
				.verifyComplete();

		ArgumentCaptor<OutboxEvento> captor = ArgumentCaptor.forClass(OutboxEvento.class);
		org.mockito.Mockito.verify(repository).save(captor.capture());
		JsonNode envelope = mapper.readTree(captor.getValue().getPayload());
		assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
		assertThat(envelope.get("pacienteId").asText()).isEqualTo(patientId.toString());
		assertThat(envelope.get("pacienteSequence").asInt()).isEqualTo(1);
		assertThat(envelope.get("occurredAt").asText()).isEqualTo(occurredAt.toString());
		assertThat(envelope.get("correlationId").asText()).isEqualTo(expectedCorrelation);
		assertThat(envelope.get("nivel").asText()).isEqualTo("CRITICO");
		assertThat(envelope.get("eventType").asText()).isEqualTo("paciente_registrado");
		assertThat(envelope.get("eventId").asText()).isEqualTo(captor.getValue().getEventId().toString());
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void capturesTheCurrentW3cTraceContextOnTheStoredEvent() {
		when(currentTraceContext.context()).thenReturn(traceContext);
		doAnswer(invocation -> {
			Map<String, String> carrier = invocation.getArgument(1);
			carrier.put("traceparent", TRACEPARENT);
			return null;
		}).when(propagator).inject(eq(traceContext), any(Map.class), any());

		StepVerifier.create(writer.write(UUID.randomUUID(), "paciente_registrado",
						Map.of("nivel", "CRITICO"), Instant.parse("2026-11-26T16:00:00Z")))
				.assertNext(event -> assertThat(event.getTraceParent()).isEqualTo(TRACEPARENT))
				.verifyComplete();
	}
}
