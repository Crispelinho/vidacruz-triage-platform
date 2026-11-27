package com.vidacruz.triageservice.application.filter;

import java.util.UUID;

import reactor.core.publisher.Mono;

/** NFR-20 correlation id carried in the Reactor context for request-triggered writes. */
public final class CorrelationId {

	public static final String HEADER = "X-Correlation-Id";
	public static final String CONTEXT_KEY = CorrelationId.class.getName();

	private CorrelationId() {
	}

	public static String fromHeader(String supplied) {
		return supplied == null || supplied.isBlank() ? UUID.randomUUID().toString() : supplied.trim();
	}

	/** Returns the current request correlation id, or a fresh one for scheduled work. */
	public static Mono<String> current() {
		return Mono.deferContextual(context -> Mono.just(
				context.hasKey(CONTEXT_KEY) ? context.get(CONTEXT_KEY) : UUID.randomUUID().toString()));
	}
}
