package com.vidacruz.clinical.infrastructure.adapters;

import java.util.List;

import com.vidacruz.clinical.domain.ports.TaskAwareEmbeddingProvider;
import com.vidacruz.clinical.infrastructure.config.GeminiEmbeddingProperties;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Real Gemini embeddings through {@code POST /v1beta/models/{model}:embedContent}.
 *
 * <p>The API key travels in the {@code x-goog-api-key} header, never in a URL. {@code gemini-embedding-2} does not
 * accept {@code task_type}: asymmetric retrieval is expressed with the task prefixes documented by Google. The API
 * does not receive patient context; callers pass only protocol text or a clinical question (OD-16).
 */
public final class GeminiEmbeddingAdapter implements TaskAwareEmbeddingProvider {

	private static final String QUERY_PREFIX = "task: question answering | query: ";

	private static final String DOCUMENT_PREFIX = "title: none | text: ";

	private final WebClient client;

	private final String model;

	private final String apiKey;

	private final int dimensions;

	private final boolean taskPrefixes;

	public GeminiEmbeddingAdapter(WebClient.Builder builder, GeminiEmbeddingProperties properties) {
		this.client = builder.baseUrl(properties.baseUrl()).build();
		this.model = properties.model();
		this.apiKey = properties.apiKey();
		this.dimensions = properties.dimensions();
		this.taskPrefixes = properties.taskPrefixes();
	}

	@Override
	public Mono<float[]> embed(String text) {
		return call(text);
	}

	@Override
	public Mono<float[]> embedQuery(String text) {
		return call(taskPrefixes ? QUERY_PREFIX + text : text);
	}

	@Override
	public Mono<float[]> embedDocument(String text) {
		return call(taskPrefixes ? DOCUMENT_PREFIX + text : text);
	}

	private Mono<float[]> call(String text) {
		if (text == null || text.isBlank()) {
			return Mono.error(new IllegalArgumentException("embedding text must not be blank"));
		}
		EmbedRequest body = new EmbedRequest(new Content(List.of(new Part(text))), dimensions);
		return client.post()
				.uri(uriBuilder -> uriBuilder.path("/{model}:embedContent").build(model))
				.header("x-goog-api-key", apiKey)
				.bodyValue(body)
				.retrieve()
				.bodyToMono(EmbedResponse.class)
				.map(response -> {
					float[] values = response.embedding().values();
					if (values == null || values.length != dimensions) {
						throw new IllegalStateException("gemini embedding returned "
								+ (values == null ? "no vector" : values.length + " dimensions")
								+ ", expected " + dimensions);
					}
					return values;
				});
	}

	record Part(String text) {
	}

	record Content(List<Part> parts) {
	}

	record EmbedRequest(Content content, @JsonProperty("output_dimensionality") Integer outputDimensionality) {
	}

	record Embedding(float[] values) {
	}

	record EmbedResponse(Embedding embedding) {
	}

}
