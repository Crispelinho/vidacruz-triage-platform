package com.vidacruz.clinical.infrastructure.adapters;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import reactor.core.publisher.Mono;

/**
 * Latency of each provider attempt in the failover chain, by how it ended (NFR-07). A failed node records one
 * sample before yielding to the next one; a call refused by an open circuit records none because the provider was
 * never called. Prometheus exposes {@code assistant_llm_latency_seconds{outcome}} with buckets up to the node timeout.
 */
public final class LlmCallMetrics {

	static final String LATENCY = "assistant.llm.latency";
	static final String OUTCOME_TAG = "outcome";
	static final String SUCCESS = "success";
	static final String TIMEOUT = "timeout";
	static final String RATE_LIMITED = "rate_limited";
	static final String SERVER_ERROR = "server_error";
	/** The provider rejected the request itself (bad key, malformed request): failing over will not help. */
	static final String REJECTED = "rejected";
	static final String NETWORK = "network";
	static final String OTHER = "other";

	private static final List<String> OUTCOMES = List.of(SUCCESS, TIMEOUT, RATE_LIMITED, SERVER_ERROR, REJECTED,
			NETWORK, OTHER);
	private static final Duration[] BUCKETS = { Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2),
			Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(15), Duration.ofSeconds(30) };
	private static final int REQUEST_TIMEOUT = 408;
	private static final int TOO_MANY_REQUESTS = 429;
	private static final int FIRST_SERVER_ERROR = 500;

	private final MeterRegistry registry;
	private final Map<String, Timer> timers = new HashMap<>();

	public LlmCallMetrics(MeterRegistry registry) {
		this.registry = registry;
		for (String outcome : OUTCOMES) {
			timers.put(outcome, Timer.builder(LATENCY)
					.description("Latency of each attempt to call an AI provider, by how it ended")
					.tag(OUTCOME_TAG, outcome)
					.serviceLevelObjectives(BUCKETS)
					.register(registry));
		}
	}

	/** Times one attempt, from subscription until it answers or fails. */
	<T> Mono<T> timed(Mono<T> attempt) {
		return Mono.defer(() -> {
			var sample = Timer.start(registry);
			return attempt
					.doOnSuccess(answer -> sample.stop(timers.get(SUCCESS)))
					.doOnError(error -> sample.stop(timers.get(outcomeOf(error))));
		});
	}

	/**
	 * The router wraps provider failures in {@code LLMUnavailableException}, so the original cause is found by walking
	 * the chain. Only the class and HTTP status of the cause are inspected: prompts and response bodies are never read.
	 */
	static String outcomeOf(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof TimeoutException) {
				return TIMEOUT;
			}
			if (cause instanceof WebClientResponseException response) {
				int status = response.getStatusCode().value();
				if (status == TOO_MANY_REQUESTS) {
					return RATE_LIMITED;
				}
				if (status == REQUEST_TIMEOUT) {
					return TIMEOUT;
				}
				if (status >= FIRST_SERVER_ERROR) {
					return SERVER_ERROR;
				}
				return REJECTED;
			}
			if (cause instanceof WebClientRequestException || cause instanceof IOException) {
				return NETWORK;
			}
		}
		return OTHER;
	}
}
