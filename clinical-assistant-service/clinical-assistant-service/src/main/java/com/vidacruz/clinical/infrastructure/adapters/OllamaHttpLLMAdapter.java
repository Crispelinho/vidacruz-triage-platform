package com.vidacruz.clinical.infrastructure.adapters;

import java.util.List;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;
import com.vidacruz.clinical.infrastructure.config.OllamaProperties;

import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/** Real Ollama node for the C17 chain. Calls {@code POST {base-url}/api/chat} with streaming disabled. */
public final class OllamaHttpLLMAdapter implements NamedLLMProvider {

	private final WebClient client;

	private final String model;

	public OllamaHttpLLMAdapter(WebClient.Builder builder, OllamaProperties properties) {
		this.client = builder.baseUrl(properties.url()).build();
		this.model = properties.model();
	}

	@Override
	public String key() {
		return "ollama";
	}

	@Override
	public String model() {
		return model;
	}

	@Override
	public Mono<String> generate(String question, List<EvidenceFragment> evidence) {
		ChatRequest body = new ChatRequest(model,
				List.of(new Message("user", GroundedPrompt.build(question, evidence))), false);
		Mono<String> answer = client.post()
				.uri("/api/chat")
				.bodyValue(body)
				.retrieve()
				.bodyToMono(ChatResponse.class)
				.map(response -> response.message().content());
		return ProviderFailures.map(key(), model, answer);
	}

	record Message(String role, String content) {
	}

	record ChatRequest(String model, List<Message> messages, boolean stream) {
	}

	record ChatResponse(Message message) {
	}

}
