package com.vidacruz.triageservice.application.filter;

import java.util.UUID;
import java.util.regex.Pattern;

import reactor.core.publisher.Mono;

/** NFR-20 correlation id carried in the Reactor context for request-triggered writes. */
public final class CorrelationId {

	public static final String HEADER = "X-Correlation-Id";
	public static final String CONTEXT_KEY = CorrelationId.class.getName();

	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

	private CorrelationId() {
	}

	/** Accepts a caller id only when it is short and log-safe; anything else gets a fresh UUID. */
	public static String fromHeader(String supplied) {
		String candidate = supplied == null ? "" : supplied.trim();
		return SAFE_ID.matcher(candidate).matches() ? candidate : UUID.randomUUID().toString();
	}

	/** Returns the current request correlation id, or a fresh one for scheduled work. */
	public static Mono<String> current() {
		return Mono.deferContextual(context -> Mono.just(
				context.hasKey(CONTEXT_KEY) ? context.get(CONTEXT_KEY) : UUID.randomUUID().toString()));
	}
}
