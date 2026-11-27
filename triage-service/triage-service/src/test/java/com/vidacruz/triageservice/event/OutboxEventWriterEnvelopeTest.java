package com.vidacruz.triageservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.domain.service.TriageRegistrationService;
import com.vidacruz.triageservice.event.domain.OutboxEvento;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventWriter;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventoRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxEventWriterEnvelopeTest {

    @Test
    void publishedPayloadContainsTheEnvelopeNeededByConsumers() throws Exception {
        var repository = mock(OutboxEventoRepository.class);
        when(repository.findFirstByPacienteIdOrderByPacienteSequenceDesc(any()))
                .thenReturn(Mono.empty());
        when(repository.save(any(OutboxEvento.class)))
                .thenAnswer(call -> Mono.just(call.getArgument(0)));

        var writer = new OutboxEventWriter(repository, new ObjectMapper(),
                io.micrometer.tracing.Tracer.NOOP, io.micrometer.tracing.propagation.Propagator.NOOP);
        var patientId = UUID.randomUUID();
        assertThat(TriageRegistrationService.REGISTERED_EVENT).isEqualTo("paciente_registrado");
        var event = writer.write(patientId, TriageRegistrationService.REGISTERED_EVENT,
                Map.of("nivel", "CRITICO", "estado", "REGISTRADO"), Instant.parse("2026-11-26T10:00:00Z"))
                .block();

        var json = new ObjectMapper().readTree(event.getPayload());
        assertThat(json.path("eventType").asText()).isEqualTo("paciente_registrado");
        assertThat(json.path("pacienteId").asText()).isEqualTo(patientId.toString());
        assertThat(json.path("pacienteSequence").asLong()).isEqualTo(1);
        assertThat(json.path("occurredAt").asText()).isEqualTo("2026-11-26T10:00:00Z");
        assertThat(json.path("eventId").asText()).isNotBlank();
        assertThat(json.path("correlationId").asText()).isNotBlank();
        assertThat(json.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.path("nivel").asText()).isEqualTo("CRITICO");
    }
}
