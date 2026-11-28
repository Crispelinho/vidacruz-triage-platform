package com.vidacruz.clinical.infrastructure.adapters;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import reactor.core.publisher.Mono;

/**
 * One span per provider attempt in the failover chain, marked as failed when the attempt errors, times out or is
 * abandoned (NFR-16). Spring AI closes its own chat span as successful when a call is cancelled, and only some
 * providers report one at all, so this is the span that tells how an attempt ended. It records the provider key and
 * the bounded outcome of {@link LlmCallMetrics}: never the prompt, the answer or an exception message.
 */
public final class LlmAttemptTracing {

	static final String NAME = "assistant.llm.attempt";
	static final String CANCELLED = "cancelled";

	private final ObservationRegistry registry;

	public LlmAttemptTracing(ObservationRegistry registry) {
		this.registry = Objects.requireNonNull(registry, "registry");
	}

	<T> Mono<T> traced(String providerKey, Mono<T> attempt) {
		return Mono.deferContextual(context -> {
			Observation observation = Observation.createNotStarted(NAME, registry)
					.contextualName("llm attempt " + providerKey)
					.lowCardinalityKeyValue("provider", providerKey)
					.parentObservation(context.getOrDefault(ObservationThreadLocalAccessor.KEY, null))
					.start();
			AtomicBoolean ended = new AtomicBoolean();
			return attempt
					.doOnSuccess(answer -> end(observation, ended, LlmCallMetrics.SUCCESS))
					.doOnError(failure -> end(observation, ended, LlmCallMetrics.outcomeOf(failure)))
					.doOnCancel(() -> end(observation, ended, CANCELLED))
					.contextWrite(inner -> inner.put(ObservationThreadLocalAccessor.KEY, observation));
		});
	}

	private static void end(Observation observation, AtomicBoolean ended, String outcome) {
		if (!ended.compareAndSet(false, true)) {
			return;
		}
		observation.lowCardinalityKeyValue("outcome", outcome);
		if (!LlmCallMetrics.SUCCESS.equals(outcome)) {
			observation.error(new ProviderCallFailed(outcome));
		}
		observation.stop();
	}

	/** Carries the outcome only: no cause and no stack trace, so nothing else can reach the span. */
	static final class ProviderCallFailed extends RuntimeException {

		private ProviderCallFailed(String outcome) {
			super("LLM provider call failed: " + outcome, null, false, false);
		}
	}
}
