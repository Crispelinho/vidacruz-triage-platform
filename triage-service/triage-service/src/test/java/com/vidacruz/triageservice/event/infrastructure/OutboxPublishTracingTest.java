package com.vidacruz.triageservice.event.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** The publication of an outbox event must appear in the trace of the request that wrote it (NFR-16). */
class OutboxPublishTracingTest {

	private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
	private static final String PARENT_ID = "00f067aa0ba902b7";
	private static final String TRACEPARENT = "00-" + TRACE_ID + "-" + PARENT_ID + "-01";

	private final List<SpanData> spans = new CopyOnWriteArrayList<>();
	private final OutboxPublishTracing tracing = tracing(spans);

	@Test
	void recordsAProducerSpanAsAChildOfTheStoredTraceContext() {
		StepVerifier.create(tracing.traced(TRACEPARENT, "triaje.eventos", Mono.just("sent")))
				.expectNext("sent").verifyComplete();

		assertThat(spans).singleElement().satisfies(span -> {
			assertThat(span.getName()).isEqualTo("triaje.eventos publish");
			assertThat(span.getKind()).isEqualTo(SpanKind.PRODUCER);
			assertThat(span.getTraceId()).isEqualTo(TRACE_ID);
			assertThat(span.getParentSpanId()).isEqualTo(PARENT_ID);
			assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
			assertThat(span.getAttributes().asMap().keySet()).extracting(Object::toString)
					.containsExactlyInAnyOrder("messaging.system", "messaging.destination.name");
		});
	}

	@Test
	void marksAFailedPublicationWithoutItsMessage() {
		Mono<String> failure = Mono.error(new IllegalStateException("SECRET payload excerpt"));

		StepVerifier.create(tracing.traced(TRACEPARENT, "triaje.eventos", failure))
				.expectError(IllegalStateException.class).verify(Duration.ofSeconds(5));

		assertThat(spans).singleElement().satisfies(span -> {
			assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
			assertThat(span.toString()).doesNotContain("SECRET");
		});
	}

	/** A publication interrupted by a shutdown leaves the row unpublished, so its span must not read as a success. */
	@Test
	void marksACancelledPublicationAsFailed() {
		StepVerifier.create(tracing.traced(TRACEPARENT, "triaje.eventos", Mono.never()))
				.thenAwait(Duration.ofMillis(50))
				.thenCancel()
				.verify(Duration.ofSeconds(5));

		assertThat(spans).singleElement().satisfies(span -> {
			assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
			assertThat(span.getStatus().getDescription()).isEqualTo("Outbox publication cancelled");
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	void isAppliedByThePublisherToEverySendOfATracedEvent() {
		OutboxEventoRepository repository = mock(OutboxEventoRepository.class);
		KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
		OutboxEvento event = new OutboxEvento(UUID.randomUUID(), "paciente_registrado", "{}", UUID.randomUUID(), 1,
				OffsetDateTime.now(), UUID.randomUUID());
		event.setTraceParent(TRACEPARENT);
		when(repository.findUnpublishedWithoutLease(10)).thenReturn(Flux.just(event));
		when(repository.claimLeaseIfAvailable(any(), any(), any())).thenReturn(Mono.just(1));
		when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
		when(repository.markPublished(any(), any())).thenReturn(Mono.empty());

		StepVerifier.create(new OutboxPublisher(repository, kafka, "triaje.eventos", 10, 30, 60_000, tracing).publish())
				.expectNext(1).verifyComplete();

		assertThat(spans).singleElement().extracting(SpanData::getName).isEqualTo("triaje.eventos publish");
	}

	/** Events written outside a trace, such as SLA escalations, must not start one of their own. */
	@Test
	void recordsNothingForAnEventWrittenOutsideATrace() {
		StepVerifier.create(tracing.traced(null, "triaje.eventos", Mono.just("sent")))
				.expectNext("sent").verifyComplete();
		StepVerifier.create(tracing.traced(" ", "triaje.eventos", Mono.just("sent")))
				.expectNext("sent").verifyComplete();

		assertThat(spans).isEmpty();
	}

	private static OutboxPublishTracing tracing(List<SpanData> sink) {
		SpanExporter exporter = new SpanExporter() {
			@Override
			public CompletableResultCode export(Collection<SpanData> batch) {
				sink.addAll(batch);
				return CompletableResultCode.ofSuccess();
			}

			@Override
			public CompletableResultCode flush() {
				return CompletableResultCode.ofSuccess();
			}

			@Override
			public CompletableResultCode shutdown() {
				return CompletableResultCode.ofSuccess();
			}
		};
		SdkTracerProvider provider = SdkTracerProvider.builder()
				.addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
		io.opentelemetry.api.trace.Tracer otel = provider.get("test");
		return new OutboxPublishTracing(
				new OtelTracer(otel, new OtelCurrentTraceContext(), event -> {
				}),
				new OtelPropagator(ContextPropagators.create(W3CTraceContextPropagator.getInstance()), otel));
	}
}
