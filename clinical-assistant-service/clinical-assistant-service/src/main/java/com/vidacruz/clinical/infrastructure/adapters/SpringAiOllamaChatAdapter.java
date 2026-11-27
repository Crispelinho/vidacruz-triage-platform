package com.vidacruz.clinical.infrastructure.adapters;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import reactor.core.publisher.Mono;

/**
 * First node of the C17 chain, backed by Spring AI (ADR-05). The call goes through {@link ChatClient} in streaming
 * mode: {@code stream().content()} is a {@code Flux<String>} driven by the {@code WebClient} inside
 * {@code OllamaApi}, so no thread blocks on the HTTP exchange. The chunks are joined into one answer because
 * {@code RAGService} validates the complete JSON (RN-05) before anything reaches the caller.
 *
 * <p>Only the transport changes: the prompt is the shared {@link GroundedPrompt}, failures are mapped by
 * {@link ProviderFailures}, and timeout, circuit breaker and failover stay in {@link ResilientLLMRouter}.
 */
public final class SpringAiOllamaChatAdapter implements NamedLLMProvider {

	private final ChatClient chatClient;

	private final String model;

	public SpringAiOllamaChatAdapter(ChatModel chatModel, String model) {
		this.chatClient = ChatClient.create(Objects.requireNonNull(chatModel, "chatModel"));
		if (model == null || model.isBlank()) {
			throw new IllegalArgumentException("model must not be blank");
		}
		this.model = model;
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
		// No user params are passed, so ChatClient sends the text as-is: the JSON braces are not template syntax.
		Mono<String> answer = Mono.defer(() -> chatClient.prompt()
				.user(GroundedPrompt.build(question, evidence))
				.stream()
				.content()
				.collect(Collectors.joining()));
		return ProviderFailures.map(key(), model, answer);
	}

}
