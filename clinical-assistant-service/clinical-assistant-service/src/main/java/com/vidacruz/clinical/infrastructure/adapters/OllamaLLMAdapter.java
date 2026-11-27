package com.vidacruz.clinical.infrastructure.adapters;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.ports.LLMProvider;
import com.vidacruz.clinical.infrastructure.config.OllamaProperties;

import reactor.core.publisher.Mono;

/**
 * Fixture stand-in for Ollama (ADR-05, option D). It never calls the network: it returns a G-04 JSON answer after the
 * configured latency and enforces the configured timeout, so resilience and citation validation can be exercised before
 * ADR-11 confirms GPU capacity. Replace the body with a real client in a later change.
 */
public class OllamaLLMAdapter implements LLMProvider {

	private static final ObjectMapper MAPPER = new ObjectMapper();

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
		return Mono.fromSupplier(() -> jsonAnswer(evidence))
				.delayElement(latency)
				.timeout(timeout)
				.onErrorMap(TimeoutException.class,
						ex -> new LLMUnavailableException("LLM call exceeded " + timeout, ex));
	}

	private String jsonAnswer(List<EvidenceFragment> evidence) {
		ArrayNode sources = MAPPER.createArrayNode();
		StringBuilder markers = new StringBuilder();
		for (int i = 0; i < evidence.size(); i++) {
			EvidenceFragment fragment = evidence.get(i);
			ObjectNode source = sources.addObject();
			source.put("protocolo", fragment.protocol());
			source.put("seccion", fragment.section());
			markers.append(" [").append(i + 1).append(']');
		}
		ObjectNode root = MAPPER.createObjectNode();
		root.put("respuesta", "[FIXTURE:" + model + "] Respuesta simulada a partir de " + evidence.size()
				+ " fragmento(s) de evidencia." + markers);
		root.set("fuentes", sources);
		return root.toString();
	}

}
