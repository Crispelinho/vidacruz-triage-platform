package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** C17 chain behavior: failover, no same-node retry, per-node breakers and terminal 503 trigger. */
class ResilientLLMRouterTest {

	private final LlmCallMetrics metrics = new LlmCallMetrics(new SimpleMeterRegistry());

	private static final List<EvidenceFragment> EVIDENCE = List.of(
			EvidenceFragment.create(UUID.randomUUID(), 0, "PROTO-001", "Medidas", "texto", new float[] { 1f }));

	private static NamedLLMProvider provider(String key, String model, Function<String, Mono<String>> answer) {
		return new NamedLLMProvider() {
			@Override
			public String key() {
				return key;
			}

			@Override
			public String model() {
				return model;
			}

			@Override
			public Mono<String> generate(String question, List<EvidenceFragment> evidence) {
				return answer.apply(question);
			}
		};
	}

	@Test
	void failsOverToTheNextNodeAndReportsTheRespondingModel() {
		AtomicInteger firstCalls = new AtomicInteger();
		NamedLLMProvider first = provider("ollama", "llama3.1:8b", question -> {
			firstCalls.incrementAndGet();
			return Mono.error(new RuntimeException("node down"));
		});
		NamedLLMProvider second = provider("gemini", "gemini-2.5-flash", question -> Mono.just("respuesta [1]"));
		ResilientLLMRouter router = new ResilientLLMRouter(List.of(first, second),
				CircuitBreakerRegistry.ofDefaults(), Duration.ofSeconds(1), metrics);

		StepVerifier.create(router.generate("q", EVIDENCE))
				.assertNext(completion -> {
					assertThat(completion.answer()).isEqualTo("respuesta [1]");
					assertThat(completion.usedModel()).isEqualTo("gemini/gemini-2.5-flash");
				})
				.verifyComplete();
		assertThat(firstCalls.get()).isEqualTo(1);
	}

	@Test
	void terminalFailureIsOneLLMUnavailableException() {
		NamedLLMProvider first = provider("ollama", "llama3.1:8b", question -> Mono.error(new RuntimeException("down")));
		NamedLLMProvider second = provider("gemini", "gemini-2.5-flash",
				question -> Mono.error(new RuntimeException("down")));
		ResilientLLMRouter router = new ResilientLLMRouter(List.of(first, second),
				CircuitBreakerRegistry.ofDefaults(), Duration.ofSeconds(1), metrics);

		StepVerifier.create(router.generate("q", EVIDENCE))
				.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(LLMUnavailableException.class))
				.verify();
	}

	@Test
	void anOpenBreakerSkipsOnlyItsOwnNode() {
		CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
		registry.circuitBreaker("ollama").transitionToOpenState();
		AtomicInteger skippedCalls = new AtomicInteger();
		NamedLLMProvider first = provider("ollama", "llama3.1:8b", question -> {
			skippedCalls.incrementAndGet();
			return Mono.just("should not be used");
		});
		NamedLLMProvider second = provider("opencode", "glm-5.3", question -> Mono.just("respuesta [1]"));
		ResilientLLMRouter router = new ResilientLLMRouter(List.of(first, second), registry, Duration.ofSeconds(1), metrics);

		StepVerifier.create(router.generate("q", EVIDENCE))
				.assertNext(completion -> assertThat(completion.usedModel()).isEqualTo("opencode/glm-5.3"))
				.verifyComplete();
		assertThat(skippedCalls.get()).isZero();
	}

	@Test
	void aSlowNodeTimesOutAndFailsOver() {
		NamedLLMProvider first = provider("ollama", "llama3.1:8b", question -> Mono.never());
		NamedLLMProvider second = provider("gemini", "gemini-2.5-flash", question -> Mono.just("respuesta [1]"));
		ResilientLLMRouter router = new ResilientLLMRouter(List.of(first, second),
				CircuitBreakerRegistry.ofDefaults(), Duration.ofMillis(20), metrics);

		StepVerifier.create(router.generate("q", EVIDENCE))
				.assertNext(completion -> assertThat(completion.usedModel()).isEqualTo("gemini/gemini-2.5-flash"))
				.expectComplete()
				.verify(Duration.ofSeconds(2));
	}

	/** Otherwise a node nobody has called yet has no breaker state to publish or alert on. */
	@Test
	void createsTheBreakerOfEveryNodeBeforeTheFirstCall() {
		CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
		NamedLLMProvider first = provider("ollama", "llama3.1:8b", question -> Mono.just("respuesta [1]"));
		NamedLLMProvider second = provider("gemini", "gemini-2.5-flash", question -> Mono.just("respuesta [1]"));

		new ResilientLLMRouter(List.of(first, second), registry, Duration.ofSeconds(1), metrics);

		assertThat(registry.find("ollama")).isPresent();
		assertThat(registry.find("gemini")).isPresent();
	}

	@Test
	void anEmptyChainFailsWithLLMUnavailable() {
		ResilientLLMRouter router = new ResilientLLMRouter(List.of(), CircuitBreakerRegistry.ofDefaults(),
				Duration.ofSeconds(1), metrics);

		StepVerifier.create(router.generate("q", EVIDENCE))
				.expectError(LLMUnavailableException.class)
				.verify();
	}

}
