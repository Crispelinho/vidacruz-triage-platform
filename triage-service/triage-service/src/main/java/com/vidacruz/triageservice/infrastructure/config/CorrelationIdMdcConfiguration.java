package com.vidacruz.triageservice.infrastructure.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;

import com.vidacruz.triageservice.application.filter.CorrelationId;

import io.micrometer.context.ContextRegistry;

/**
 * Copies the request correlation id from the Reactor context into the logging MDC (NFR-20).
 *
 * <p>With {@code spring.reactor.context-propagation=auto}, Reactor restores registered thread locals around each
 * operator, so every log line written inside a request pipeline carries {@code correlationId} next to the
 * {@code traceId}/{@code spanId} that Micrometer Tracing already adds.
 */
@Configuration(proxyBeanMethods = false)
public class CorrelationIdMdcConfiguration {

	public static final String MDC_KEY = "correlationId";

	public CorrelationIdMdcConfiguration() {
		register();
	}

	/** Registering the same key again replaces the previous accessor, so repeated contexts are safe. */
	public static void register() {
		ContextRegistry.getInstance().registerThreadLocalAccessor(CorrelationId.CONTEXT_KEY,
				() -> MDC.get(MDC_KEY),
				value -> MDC.put(MDC_KEY, value),
				() -> MDC.remove(MDC_KEY));
	}
}
