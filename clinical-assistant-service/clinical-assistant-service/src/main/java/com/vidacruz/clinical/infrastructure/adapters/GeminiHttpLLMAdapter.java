package com.vidacruz.clinical.infrastructure.adapters;

import java.util.List;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;
import com.vidacruz.clinical.infrastructure.config.GeminiProperties;

import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Second node of the C17 chain. Calls the Google AI Studio {@code generateContent} endpoint. The API key is sent in a
 * header so it never appears in a URL or a log line.
 */
public final class GeminiHttpLLMAdapter implements NamedLLMProvider {

	private final WebClient client;

	private final String model;

	private final String apiKey;

	public GeminiHttpLLMAdapter(WebClient.Builder builder, GeminiProperties properties) {
		this.client = builder.baseUrl(properties.baseUrl()).build();
		this.model = properties.model();
		this.apiKey = properties.apiKey();
	}

	@Override
	public String key() {
		return "gemini";
	}

	@Override
	public String model() {
		return model;
	}

	@Override
	public Mono<String> generate(String question, List<EvidenceFragment> evidence) {
		// M-1: the trusted instruction goes in systemInstruction; the delimited question and evidence go in contents.
		GenerateRequest body = new GenerateRequest(new Content(List.of(new Part(GroundedPrompt.systemInstruction()))),
				List.of(new Content(List.of(new Part(GroundedPrompt.userContent(question, evidence))))));
		Mono<String> answer = client.post()
				.uri(uriBuilder -> uriBuilder.path("/{model}:generateContent").build(model))
				.header("x-goog-api-key", apiKey)
				.bodyValue(body)
				.retrieve()
				.bodyToMono(GenerateResponse.class)
				.map(response -> response.candidates().get(0).content().parts().get(0).text());
		return ProviderFailures.map(key(), model, answer);
	}

	record Part(String text) {
	}

	record Content(List<Part> parts) {
	}

	record GenerateRequest(Content systemInstruction, List<Content> contents) {
	}

	record ResponseContent(List<Part> parts) {
	}

	record Candidate(ResponseContent content) {
	}

	record GenerateResponse(List<Candidate> candidates) {
	}

}
