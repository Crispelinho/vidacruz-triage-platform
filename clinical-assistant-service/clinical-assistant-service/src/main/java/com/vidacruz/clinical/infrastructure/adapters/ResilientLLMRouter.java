package com.vidacruz.clinical.infrastructure.adapters;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.LLMCompletion;
import com.vidacruz.clinical.domain.ports.LLMRouter;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Mono;

/**
 * Ordered failover across LLM nodes (C17 developer record). Each node has its own circuit breaker from the registry,
 * its own timeout, and no retry: a failed node yields to the next one. A terminal failure is a single
 * {@link LLMUnavailableException}, mapped to HTTP 503 by the controller advice.
 */
public final class ResilientLLMRouter implements LLMRouter {

	private final List<NamedLLMProvider> providers;

	private final CircuitBreakerRegistry registry;

	private final Duration timeout;

	private final LlmCallMetrics metrics;

	public ResilientLLMRouter(List<NamedLLMProvider> providers, CircuitBreakerRegistry registry, Duration timeout,
			LlmCallMetrics metrics) {
		this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
		this.registry = Objects.requireNonNull(registry, "registry");
		this.timeout = Objects.requireNonNull(timeout, "timeout");
		this.metrics = Objects.requireNonNull(metrics, "metrics");
		if (timeout.isZero() || timeout.isNegative()) {
			throw new IllegalArgumentException("LLM timeout must be positive");
		}
	}

	@Override
	public Mono<LLMCompletion> generate(String question, List<EvidenceFragment> evidence) {
		if (providers.isEmpty()) {
			return Mono.error(new LLMUnavailableException("no LLM provider is configured"));
		}
		return call(0, question, evidence);
	}

	private Mono<LLMCompletion> call(int index, String question, List<EvidenceFragment> evidence) {
		NamedLLMProvider provider = providers.get(index);
		CircuitBreaker breaker = registry.circuitBreaker(provider.key());
		Mono<LLMCompletion> current = Mono.defer(() -> provider.generate(question, evidence))
				.timeout(timeout)
				.transform(metrics::timed)
				.transform(mono -> ProviderFailures.map(provider.key(), provider.model(), mono))
				.transformDeferred(CircuitBreakerOperator.of(breaker))
				.onErrorMap(CallNotPermittedException.class,
						error -> new LLMUnavailableException(
								provider.key() + "/" + provider.model() + " circuit is open", error))
				.map(answer -> new LLMCompletion(provider.key(), provider.model(), answer));

		if (index == providers.size() - 1) {
			return current;
		}
		return current.onErrorResume(LLMUnavailableException.class,
				error -> call(index + 1, question, evidence));
	}

}
