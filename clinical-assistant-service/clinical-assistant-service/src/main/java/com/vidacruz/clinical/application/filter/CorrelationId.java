package com.vidacruz.clinical.application.filter;

import java.util.UUID;
import java.util.regex.Pattern;

/** NFR-20 correlation id: one value per request for the response header, error bodies and log lines. */
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
}
