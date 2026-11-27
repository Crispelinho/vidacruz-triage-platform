package com.vidacruz.clinical.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * OpenRouter settings for the optional chat node of the C17 failover chain. The node is not part of the default chain;
 * the API key is supplied only through {@code OPENROUTER_API_KEY}.
 */
@ConfigurationProperties(prefix = "openrouter")
public record OpenRouterProperties(
		@DefaultValue("https://openrouter.ai/api/v1") String baseUrl,
		@DefaultValue("nvidia/nemotron-3.5-lightning:free") String model,
		@DefaultValue("") String apiKey,
		@DefaultValue("deny") String dataCollection) {

	public OpenRouterProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalArgumentException("openrouter.base-url must not be blank");
		}
		if (model == null || model.isBlank()) {
			throw new IllegalArgumentException("openrouter.model must not be blank");
		}
		if (!"deny".equalsIgnoreCase(dataCollection)) {
			throw new IllegalArgumentException("openrouter.data-collection must be deny");
		}
		apiKey = apiKey == null ? "" : apiKey.trim();
	}

}
