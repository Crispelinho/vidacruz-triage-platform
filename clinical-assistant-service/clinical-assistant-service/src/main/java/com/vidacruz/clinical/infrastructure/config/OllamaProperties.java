package com.vidacruz.clinical.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Ollama settings for the first node of the C17 chain. {@code url} binds from {@code OLLAMA_BASE_URL}; {@code url} and
 * {@code model} are forwarded to Spring AI as {@code spring.ai.ollama.base-url} and
 * {@code spring.ai.ollama.chat.options.model} in {@code application.yaml} (ADR-05). The fixture latency is retained
 * for tests that must not call the network.
 *
 * @param timeout hard limit for one LLM call; exceeding it is an {@code LLMUnavailableException} (ADR-09)
 */
@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(@DefaultValue("http://localhost:11434") String url,
		@DefaultValue("llama3.1:8b") String model, @DefaultValue("15s") Duration timeout,
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
