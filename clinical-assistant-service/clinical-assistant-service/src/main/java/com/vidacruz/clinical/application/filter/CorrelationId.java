package com.vidacruz.clinical.application.filter;

import java.util.UUID;

/** NFR-20 correlation id: one value per request for the response header, error bodies and log lines. */
public final class CorrelationId {

	public static final String HEADER = "X-Correlation-Id";
	public static final String CONTEXT_KEY = CorrelationId.class.getName();

	private CorrelationId() {
	}

	public static String fromHeader(String supplied) {
		return supplied == null || supplied.isBlank() ? UUID.randomUUID().toString() : supplied.trim();
	}
}
