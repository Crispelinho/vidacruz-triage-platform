package com.vidacruz.clinical.infrastructure.adapters;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;
import com.vidacruz.clinical.infrastructure.config.OpenRouterProperties;

import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Optional OpenRouter node of the C17 chain: OpenAI-compatible Chat Completions with the shared grounded prompt.
 * Every request carries {@code provider.data_collection=deny} and a zero price ceiling, so free endpoints cannot fall
 * back to a paid model. The ZDR flag is deliberately absent because free endpoints do not support it.
 */
public final class OpenRouterHttpLLMAdapter implements NamedLLMProvider {

	private final WebClient client;

	private final String model;

	private final String apiKey;

	private final String dataCollection;

	public OpenRouterHttpLLMAdapter(WebClient.Builder builder, OpenRouterProperties properties) {
		this.client = builder.baseUrl(properties.baseUrl()).build();
		this.model = properties.model();
		this.apiKey = properties.apiKey();
		this.dataCollection = properties.dataCollection();
	}

	@Override
	public String key() {
		return "openrouter";
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
						new Message("user", GroundedPrompt.userContent(question, evidence))),
				new Provider(dataCollection, new MaxPrice(0, 0)));
		Mono<String> answer = client.post()
				.uri("/chat/completions")
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

	record ChatRequest(String model, List<Message> messages, Provider provider) {
	}

	record Provider(@JsonProperty("data_collection") String dataCollection,
			@JsonProperty("max_price") MaxPrice maxPrice) {
	}

	record MaxPrice(int prompt, int completion) {
	}

	record ChatResponse(List<Choice> choices) {
	}

	record Choice(Message message) {
	}

}
