package com.vidacruz.clinical.domain.services;

import java.util.List;
import java.util.Objects;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.LLMCompletion;
import com.vidacruz.clinical.domain.models.PromptRequest;
import com.vidacruz.clinical.domain.models.RAGResponse;
import com.vidacruz.clinical.domain.models.ScoredEvidenceFragment;
import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.LLMProvider;
import com.vidacruz.clinical.domain.ports.LLMRouter;
import com.vidacruz.clinical.domain.ports.VectorStore;

import reactor.core.publisher.Mono;

/**
 * RAG use case: embed the question, retrieve evidence, generate a grounded answer.
 *
 * <p>Pipeline: validate ({@link PromptRequest}) -> embed -> retrieve top-K -> relevance gate -> generate -> validate citations.
 * RN-05.3 gates before any LLM call: A) no retrieved fragments, B) all fragments below relevance threshold.
 * Citation markers in the answer are validated against retrieved evidence; invalid/invented citations are rejected (RN-05.3-C).
 */
public class RAGService {

	private final EmbeddingProvider embeddingProvider;

	private final VectorStore vectorStore;

	private final LLMRouter llmRouter;

	private final double relevanceThreshold;

	public RAGService(EmbeddingProvider embeddingProvider, VectorStore vectorStore, LLMRouter llmRouter) {
		this(embeddingProvider, vectorStore, llmRouter, 0.0d);
	}

	public RAGService(EmbeddingProvider embeddingProvider, VectorStore vectorStore, LLMRouter llmRouter,
			double relevanceThreshold) {
		this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
		this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
		this.llmRouter = Objects.requireNonNull(llmRouter, "llmRouter");
		if (!Double.isFinite(relevanceThreshold) || relevanceThreshold < 0.0d || relevanceThreshold > 1.0d) {
			throw new IllegalArgumentException("relevanceThreshold must be between 0 and 1");
		}
		this.relevanceThreshold = relevanceThreshold;
	}

	/** Compatibility constructor for a single provider; production wiring uses the resilient router. */
	public RAGService(EmbeddingProvider embeddingProvider, VectorStore vectorStore, LLMProvider llmProvider) {
		this(embeddingProvider, vectorStore, (LLMRouter) (question, evidence) -> llmProvider
				.generate(question, evidence).map(answer -> new LLMCompletion("single", "unknown", answer)));
	}

	public Mono<RAGResponse> answer(PromptRequest request) {
		double threshold = request.relevanceThreshold() != null ? request.relevanceThreshold() : relevanceThreshold;
		return embeddingProvider.embed(request.question())
				.flatMap(embedding -> vectorStore.similaritySearchWithScores(embedding, request.topK()))
				.flatMap(scored -> {
					// RN-05.3-A: no results retrieved.
					if (scored.isEmpty()) {
						return Mono.just(RAGResponse.noEvidence());
					}
					List<EvidenceFragment> relevant = scored.stream()
							.filter(fragment -> fragment.score() >= threshold)
							.map(ScoredEvidenceFragment::fragment)
							.toList();
					// RN-05.3-B: results exist but none pass relevance threshold.
					if (relevant.isEmpty()) {
						return Mono.just(RAGResponse.noEvidence());
					}
					return llmRouter.generate(request.question(), relevant)
							.map(completion -> {
								// RN-05.3-C: reject answers citing evidence not retrieved in the allowed set.
								CitationValidator.resolve(completion.answer(), relevant);
								return new RAGResponse(completion.answer(), relevant, completion.usedModel());
							});
				});
	}

}
