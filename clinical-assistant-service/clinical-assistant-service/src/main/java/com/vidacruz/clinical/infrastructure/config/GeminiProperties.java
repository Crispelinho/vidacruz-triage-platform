package com.vidacruz.clinical.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Google AI Studio settings for the second node of the C17 failover chain. */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
		@DefaultValue("https://generativelanguage.googleapis.com/v1beta/models") String baseUrl,
		@DefaultValue("gemini-3.5-flash-lite") String model,
		@DefaultValue("") String apiKey) {

	public GeminiProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalArgumentException("gemini.base-url must not be blank");
		}
		if (model == null || model.isBlank()) {
			throw new IllegalArgumentException("gemini.model must not be blank");
		}
		apiKey = apiKey == null ? "" : apiKey.trim();
	}

}
