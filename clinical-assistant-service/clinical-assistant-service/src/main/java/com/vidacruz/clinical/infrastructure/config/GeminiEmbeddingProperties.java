package com.vidacruz.clinical.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Gemini embedding settings, separate from the chat model settings in {@link GeminiProperties}. */
@ConfigurationProperties(prefix = "gemini.embedding")
public record GeminiEmbeddingProperties(
		@DefaultValue("https://generativelanguage.googleapis.com/v1beta/models") String baseUrl,
		@DefaultValue("gemini-embedding-2") String model,
		@DefaultValue("") String apiKey,
		@DefaultValue("768") int dimensions,
		@DefaultValue("true") boolean taskPrefixes) {

	public GeminiEmbeddingProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalArgumentException("gemini.embedding.base-url must not be blank");
		}
		if (model == null || model.isBlank()) {
			throw new IllegalArgumentException("gemini.embedding.model must not be blank");
		}
		if (dimensions <= 0) {
			throw new IllegalArgumentException("gemini.embedding.dimensions must be positive");
		}
		apiKey = apiKey == null ? "" : apiKey.trim();
	}

}
