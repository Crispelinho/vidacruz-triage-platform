package com.vidacruz.triageservice.event.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

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
