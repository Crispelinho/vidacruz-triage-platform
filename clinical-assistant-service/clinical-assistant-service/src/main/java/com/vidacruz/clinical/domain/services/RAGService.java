package com.vidacruz.clinical.domain.services;

import java.util.Objects;

import com.vidacruz.clinical.domain.models.PromptRequest;
import com.vidacruz.clinical.domain.models.RAGResponse;
import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.LLMProvider;
import com.vidacruz.clinical.domain.ports.VectorStore;

import reactor.core.publisher.Mono;

/**
 * RAG use case: embed the question, retrieve evidence, generate a grounded answer.
 *
 * <p>Scaffold scope: the relevance gate is "no retrieved fragments". A similarity-score threshold and citation
 * validation (RN-05, R-10) belong to C-06 and are not implemented here.
 */
public class RAGService {

	private final EmbeddingProvider embeddingProvider;

	private final VectorStore vectorStore;

	private final LLMProvider llmProvider;

	public RAGService(EmbeddingProvider embeddingProvider, VectorStore vectorStore, LLMProvider llmProvider) {
		this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
		this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
		this.llmProvider = Objects.requireNonNull(llmProvider, "llmProvider");
	}

	public Mono<RAGResponse> answer(PromptRequest request) {
		return embeddingProvider.embed(request.question())
				.flatMap(embedding -> vectorStore.similaritySearch(embedding, request.topK()))
				.flatMap(fragments -> {
					if (fragments.isEmpty()) {
						// RN-05: no evidence means the fixed notice and no LLM call.
						return Mono.just(RAGResponse.noEvidence());
					}
					return llmProvider.generate(request.question(), fragments)
							.map(answer -> new RAGResponse(answer, fragments));
				});
	}

}
