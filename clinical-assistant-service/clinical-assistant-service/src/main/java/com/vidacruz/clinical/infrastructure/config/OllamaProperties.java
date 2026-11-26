package com.vidacruz.clinical.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Ollama settings. The model is provisional until ADR-05 / ADR-11 are accepted. The adapter is fixture-only for now,
 * so {@code fixture.latency} simulates the model's response time.
 *
 * @param timeout hard limit for one LLM call; exceeding it is an {@code LLMUnavailableException} (ADR-09)
 */
@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(@DefaultValue("http://localhost:11434") String url,
		@DefaultValue("llama3.1:8b") String model, @DefaultValue("30s") Duration timeout,
		@DefaultValue Fixture fixture) {

	public OllamaProperties {
		if (timeout == null || timeout.isZero() || timeout.isNegative()) {
			throw new IllegalArgumentException("ollama.timeout must be positive");
		}
	}

	/** @param latency simulated response time of the fixture model */
	public record Fixture(@DefaultValue("1200ms") Duration latency) {

		public Fixture {
			if (latency == null || latency.isNegative()) {
				throw new IllegalArgumentException("ollama.fixture.latency must not be negative");
			}
		}

	}

}
