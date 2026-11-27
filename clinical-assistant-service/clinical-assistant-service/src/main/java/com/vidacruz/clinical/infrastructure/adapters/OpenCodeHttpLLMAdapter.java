package com.vidacruz.clinical.infrastructure.adapters;

import java.util.List;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;
import com.vidacruz.clinical.infrastructure.config.OpenCodeProperties;

import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/** Third node of the C17 chain: OpenAI-compatible Chat Completions against the OpenCode Inference API. */
public final class OpenCodeHttpLLMAdapter implements NamedLLMProvider {

	private final WebClient client;

	private final String model;

	private final String apiKey;

	public OpenCodeHttpLLMAdapter(WebClient.Builder builder, OpenCodeProperties properties) {
		this.client = builder.baseUrl(properties.url()).build();
		this.model = properties.model();
		this.apiKey = properties.apiKey();
	}

	@Override
	public String key() {
		return "opencode";
	}

	@Override
	public String model() {
		return model;
	}

	@Override
	public Mono<String> generate(String question, List<EvidenceFragment> evidence) {
		ChatRequest body = new ChatRequest(model,
				// M-1: the trusted instruction is the system message; only delimited data goes in the user message.
				List.of(new Message("system", GroundedPrompt.systemInstruction()),
						new Message("user", GroundedPrompt.userContent(question, evidence))));
		Mono<String> answer = client.post()
				.headers(headers -> {
					if (!apiKey.isBlank()) {
						headers.setBearerAuth(apiKey);
					}
				})
				.bodyValue(body)
				.retrieve()
				.bodyToMono(ChatResponse.class)
				.map(response -> response.choices().get(0).message().content());
		return ProviderFailures.map(key(), model, answer);
	}

	record Message(String role, String content) {
	}

	record ChatRequest(String model, List<Message> messages) {
	}

	record ChatResponse(List<Choice> choices) {
	}

	record Choice(Message message) {
	}

}
