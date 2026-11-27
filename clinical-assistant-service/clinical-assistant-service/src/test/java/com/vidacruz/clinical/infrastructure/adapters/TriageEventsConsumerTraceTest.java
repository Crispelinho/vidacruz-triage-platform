package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vidacruz.clinical.domain.services.ProjectTriageEventUseCase;
import com.vidacruz.clinical.infrastructure.config.TriageEventsProperties;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.receiver.observation.KafkaRecordReceiverContext;
import reactor.test.StepVerifier;

/** The assistant must continue the request trace carried by the triage event (NFR-16). */
@ExtendWith(MockitoExtension.class)
class TriageEventsConsumerTraceTest {

    private static final TopicPartition PARTITION = new TopicPartition("triaje.eventos", 1);
    private static final String PATIENT = "11111111-1111-4111-8111-111111111111";
    private static final String TRACE_PARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    private static final TriageEventsProperties PROPERTIES =
            new TriageEventsProperties("localhost:9092", "triaje.eventos", "assistant");

    private static final String EVENT = """
            {"eventType":"EVT_01_PACIENTE_REGISTRADO","eventId":"22222222-2222-4222-8222-222222222222",
             "schemaVersion":1,"pacienteId":"%s","pacienteSequence":1,
             "occurredAt":"2026-11-26T10:00:00Z","correlationId":"33333333-3333-4333-8333-333333333333",
             "nivel":"CRITICO","slaMinutos":5,"estado":"REGISTRADO"}
            """.formatted(PATIENT);

    @Mock
    private ProjectTriageEventUseCase projectTriageEvent;

    @Mock
    private ReceiverOffset offset;

    private final List<Observation.Context> observed = new CopyOnWriteArrayList<>();
    private final ObservationRegistry observations = ObservationRegistry.create();

    @BeforeEach
    void recordStoppedObservations() {
        observations.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public void onStop(Observation.Context context) {
                observed.add(context);
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
    }

    @Test
    void readsTheTraceparentHeaderOfTheRecordInsideTheObservation() {
        when(projectTriageEvent.project(any())).thenReturn(Mono.just(true));

        StepVerifier.create(consumer().handle(receivedWithTrace())).expectNext(offset).verifyComplete();

        assertThat(observed).singleElement().isInstanceOfSatisfying(KafkaRecordReceiverContext.class, context -> {
            assertThat(context.getGetter().get(context.getCarrier(), "traceparent")).isEqualTo(TRACE_PARENT);
            assertThat(context.getError()).isNull();
        });
    }

    @Test
    void recordsProjectionFailuresInTheObservation() {
        IllegalStateException failure = new IllegalStateException("storage unavailable");
        when(projectTriageEvent.project(any())).thenReturn(Mono.error(failure));

        StepVerifier.create(consumer().handle(receivedWithTrace()))
                .expectErrorMatches(error -> error == failure)
                .verify();

        assertThat(observed).singleElement().satisfies(context ->
                assertThat(context.getError()).isSameAs(failure));
    }

    private TriageEventsConsumer consumer() {
        return new TriageEventsConsumer(projectTriageEvent, PROPERTIES, new SimpleMeterRegistry(), observations);
    }

    private ReceiverRecord<String, String> receivedWithTrace() {
        var record = new ConsumerRecord<>(PARTITION.topic(), PARTITION.partition(), 42L, PATIENT, EVENT);
        record.headers().add("traceparent", TRACE_PARENT.getBytes(StandardCharsets.UTF_8));
        return new ReceiverRecord<>(record, offset);
    }
}
