package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** A failed, timed-out or abandoned provider call must show as an error in the trace, without any free text. */
class LlmAttemptTracingTest {

	private static final String QUESTION = "SECRET-QUESTION";
	private static final String LEAKY_MESSAGE = "500 from POST https://provider.example/chat?key=SECRET-KEY";

	private final List<SpanData> spans = new CopyOnWriteArrayList<>();
	private final ObservationRegistry observations = tracingRegistry(spans);

	@Test
	void marksASuccessfulAttemptAsNotFailed() {
		StepVerifier.create(router(Mono.just("answer")).generate(QUESTION, List.of()))
				.expectNextCount(1).verifyComplete();

		SpanData span = attemptSpan();
		assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
		assertThat(attributes(span)).contains("provider=ollama", "outcome=success");
	}

	@Test
	void marksATimedOutAttemptAsFailed() {
		StepVerifier.create(router(Mono.never()).generate(QUESTION, List.of()))
				.expectError(LLMUnavailableException.class).verify(Duration.ofSeconds(5));

		SpanData span = attemptSpan();
		assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
		assertThat(span.getStatus().getDescription()).isEqualTo("LLM provider call failed: timeout");
		assertThat(attributes(span)).contains("outcome=timeout");
	}

	@Test
	void marksAProviderErrorWithoutCopyingItsMessageIntoTheSpan() {
		Mono<String> failure = Mono.error(WebClientResponseException.create(500, LEAKY_MESSAGE, HttpHeaders.EMPTY,
				"SECRET-BODY".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

		StepVerifier.create(router(failure).generate(QUESTION, List.of()))
				.expectError(LLMUnavailableException.class).verify(Duration.ofSeconds(5));

		SpanData span = attemptSpan();
		assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
		assertThat(attributes(span)).contains("outcome=server_error");
		assertThat(span.toString()).doesNotContain("SECRET-");
	}

	@Test
	void marksACancelledAttemptAsFailed() {
		StepVerifier.create(router(Mono.never()).generate(QUESTION, List.of()))
				.thenAwait(Duration.ofMillis(50)).thenCancel().verify(Duration.ofSeconds(5));

		SpanData span = attemptSpan();
		assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
		assertThat(attributes(span)).contains("outcome=cancelled");
	}

	/** Spring AI reads the parent observation from the Reactor context, so its chat span nests under the attempt. */
	@Test
	void exposesTheAttemptAsTheParentOfWhateverTheProviderObserves() {
		AtomicReference<Observation> seen = new AtomicReference<>();
		Mono<String> provider = Mono.deferContextual(context -> {
			seen.set(context.getOrDefault(ObservationThreadLocalAccessor.KEY, null));
			return Mono.just("answer");
		});

		StepVerifier.create(router(provider).generate(QUESTION, List.of())).expectNextCount(1).verifyComplete();

		assertThat(seen.get()).isNotNull();
		assertThat(seen.get().getContext().getName()).isEqualTo(LlmAttemptTracing.NAME);
	}

	private ResilientLLMRouter router(Mono<String> result) {
		NamedLLMProvider provider = new NamedLLMProvider() {
			@Override
			public String key() {
				return "ollama";
			}

			@Override
			public String model() {
				return "model";
			}

			@Override
			public Mono<String> generate(String question, List<EvidenceFragment> evidence) {
				return result;
			}
		};
		return new ResilientLLMRouter(List.of(provider), CircuitBreakerRegistry.ofDefaults(), Duration.ofMillis(200),
				new LlmCallMetrics(new SimpleMeterRegistry()), new LlmAttemptTracing(observations));
	}

	private SpanData attemptSpan() {
		assertThat(spans).hasSize(1);
		return spans.get(0);
	}

	private static List<String> attributes(SpanData span) {
		return span.getAttributes().asMap().entrySet().stream()
				.map(entry -> entry.getKey().getKey() + "=" + entry.getValue()).toList();
	}

	private static ObservationRegistry tracingRegistry(List<SpanData> sink) {
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
		OtelTracer tracer = new OtelTracer(provider.get("test"), new OtelCurrentTraceContext(), event -> {
		});
		ObservationRegistry registry = ObservationRegistry.create();
		registry.observationConfig().observationHandler(new DefaultTracingObservationHandler(tracer));
		return registry;
	}
}
