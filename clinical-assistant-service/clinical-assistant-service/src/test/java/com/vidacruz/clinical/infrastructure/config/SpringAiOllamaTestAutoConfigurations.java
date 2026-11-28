package com.vidacruz.clinical.infrastructure.config;

import org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;

/**
 * The auto-configurations that {@code spring-ai-starter-model-ollama} contributes to the running service, so
 * {@link org.springframework.boot.test.context.runner.ApplicationContextRunner} tests wire the same
 * {@code OllamaChatModel} bean as production. The embedding auto-configuration is listed on purpose: tests assert that
 * {@code spring.ai.model.embedding=none} keeps it off.
 */
final class SpringAiOllamaTestAutoConfigurations {

	static final AutoConfigurations ALL = AutoConfigurations.of(JacksonAutoConfiguration.class,
			CodecsAutoConfiguration.class, WebClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
			ToolCallingAutoConfiguration.class, OllamaApiAutoConfiguration.class, OllamaChatAutoConfiguration.class,
			OllamaEmbeddingAutoConfiguration.class);

	private SpringAiOllamaTestAutoConfigurations() {
	}

}
