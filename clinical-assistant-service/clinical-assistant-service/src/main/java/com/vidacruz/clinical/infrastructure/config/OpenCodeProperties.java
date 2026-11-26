package com.vidacruz.clinical.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** OpenCode Inference API settings for the third node of the C17 failover chain. */
@ConfigurationProperties(prefix = "opencode")
public record OpenCodeProperties(
		@DefaultValue("https://opencode.ai/inference/openai/v1/chat/completions") String url,
		@DefaultValue("glm-5.3") String model,
		@DefaultValue("") String apiKey) {

	public OpenCodeProperties {
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("opencode.url must not be blank");
		}
		if (model == null || model.isBlank()) {
			throw new IllegalArgumentException("opencode.model must not be blank");
		}
		apiKey = apiKey == null ? "" : apiKey.trim();
	}

}
