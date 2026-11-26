package com.vidacruz.clinical.infrastructure.adapters;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.ports.LLMProvider;
import com.vidacruz.clinical.infrastructure.config.OllamaProperties;

import reactor.core.publisher.Mono;

/**
 * Fixture stand-in for Ollama (ADR-05, option D). It never calls the network: it returns a canned answer after the
 * configured latency and enforces the configured timeout, so resilience behavior can be exercised before ADR-11
 * confirms GPU capacity. Replace the body with a real client (Spring AI {@code ChatClient}) in a later change.
 */
public class OllamaLLMAdapter implements LLMProvider {

	private final String model;

	private final Duration latency;

	private final Duration timeout;

	public OllamaLLMAdapter(OllamaProperties properties) {
		this.model = properties.model();
		this.latency = properties.fixture().latency();
		this.timeout = properties.timeout();
	}

	@Override
	public Mono<String> generate(String question, List<EvidenceFragment> evidence) {
		// Neither the question nor the evidence text is echoed or logged (CA-G-16).
		return Mono.fromSupplier(() -> "[FIXTURE:" + model + "] Respuesta simulada a partir de " + evidence.size()
				+ " fragmento(s) de evidencia.")
				.delayElement(latency)
				.timeout(timeout)
				.onErrorMap(TimeoutException.class,
						ex -> new LLMUnavailableException("LLM call exceeded " + timeout, ex));
	}

}
