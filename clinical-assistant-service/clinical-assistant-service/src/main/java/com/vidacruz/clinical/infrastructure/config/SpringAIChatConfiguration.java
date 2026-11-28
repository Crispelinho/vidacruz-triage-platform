package com.vidacruz.clinical.infrastructure.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit Spring AI chat beans for the C17 chain. Three {@link ChatClient} instances coexist; the default
 * {@code ChatClient} auto-configuration is disabled because it expects a single {@code ChatModel}. Only {@code ollama}
 * comes from a starter; {@code gemini} and {@code opencode} are built from the service's own properties so the
 * heterogeneous providers do not need three auto-configurations and their incompatible property namespaces.
 */
@Configuration(proxyBeanMethods = false)
class SpringAIChatConfiguration {

	private static final String UNCONFIGURED_KEY = "unconfigured";

	/** @return the Google AI Studio client root, derived from the legacy chat endpoint property. */
	private static String genAiRootUrl(String configured) {
		String suffix = "/v1beta/models";
		return configured.endsWith(suffix) ? configured.substring(0, configured.length() - suffix.length()) : configured;
	}

	/** @return the OpenAI-compatible API root; the legacy property is the full chat completions URL. */
	private static String openAiRootUrl(String configured) {
		String suffix = "/chat/completions";
		return configured.endsWith(suffix) ? configured.substring(0, configured.length() - suffix.length()) : configured;
	}

	@Bean
	GoogleGenAiChatModel geminiChatModel(GeminiProperties properties) {
		Client client = Client.builder()
				.apiKey(properties.apiKey().isBlank() ? UNCONFIGURED_KEY : properties.apiKey())
				.httpOptions(HttpOptions.builder().baseUrl(genAiRootUrl(properties.baseUrl())).build())
				.build();
		return GoogleGenAiChatModel.builder()
				.genAiClient(client)
				.defaultOptions(GoogleGenAiChatOptions.builder().model(properties.model()).build())
				.build();
	}

	@Bean
	OpenAiChatModel opencodeChatModel(OpenCodeProperties properties) {
		OpenAiApi api = OpenAiApi.builder()
				.baseUrl(openAiRootUrl(properties.url()))
				.apiKey(properties.apiKey().isBlank() ? UNCONFIGURED_KEY : properties.apiKey())
				.build();
		return OpenAiChatModel.builder()
				.openAiApi(api)
				.defaultOptions(OpenAiChatOptions.builder().model(properties.model()).build())
				.build();
	}

	@Bean
	@Qualifier("ollamaChatClient")
	ChatClient ollamaChatClient(OllamaChatModel model) {
		return ChatClient.create(model);
	}

	@Bean
	@Qualifier("geminiChatClient")
	ChatClient geminiChatClient(@Qualifier("geminiChatModel") GoogleGenAiChatModel model) {
		return ChatClient.create(model);
	}

	@Bean
	@Qualifier("opencodeChatClient")
	ChatClient opencodeChatClient(@Qualifier("opencodeChatModel") OpenAiChatModel model) {
		return ChatClient.create(model);
	}

}
