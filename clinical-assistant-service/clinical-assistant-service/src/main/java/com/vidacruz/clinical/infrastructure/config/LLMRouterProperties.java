package com.vidacruz.clinical.infrastructure.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Ordered node list and per-provider timeout for the C17 LLM chain. The chain binds from
 * {@code RAG_LLM_CHAIN} through {@code rag.llm.chain}.
 */
@ConfigurationProperties(prefix = "rag.llm")
public record LLMRouterProperties(@DefaultValue("ollama,gemini,opencode") String chain,
		@DefaultValue("15s") Duration timeout) {

	public LLMRouterProperties {
		if (chain == null || Arrays.stream(chain.split(",")).map(String::trim).noneMatch(key -> !key.isEmpty())) {
			throw new IllegalArgumentException("rag.llm.chain must name at least one provider");
		}
		if (timeout == null || timeout.isZero() || timeout.isNegative()) {
			throw new IllegalArgumentException("rag.llm.timeout must be positive");
		}
	}

	public List<String> providerKeys() {
		return Arrays.stream(chain.split(","))
				.map(String::trim)
				.filter(key -> !key.isEmpty())
				.toList();
	}

}
