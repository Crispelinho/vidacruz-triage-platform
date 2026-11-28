package com.vidacruz.clinical.domain.services;

import java.util.List;
import java.util.Objects;

import com.vidacruz.clinical.domain.models.Citation;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.LLMCompletion;
import com.vidacruz.clinical.domain.models.PromptRequest;
import com.vidacruz.clinical.domain.models.RAGResponse;
import com.vidacruz.clinical.domain.models.ScoredEvidenceFragment;
import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.GroundedAnswerDecoder;
import com.vidacruz.clinical.domain.ports.LLMProvider;
import com.vidacruz.clinical.domain.ports.LLMRouter;
import com.vidacruz.clinical.domain.ports.TaskAwareEmbeddingProvider;
import com.vidacruz.clinical.domain.ports.VectorStore;

import reactor.core.publisher.Mono;

/**
 * RAG use case: embed the question, retrieve evidence, generate a grounded answer.
 *
 * <p>Pipeline: validate -> embed -> retrieve top-K -> relevance gate -> generate -> parse G-04 JSON -> validate citations
 * against the corpus index and the retrieved set. The LLM is called only when at least one fragment passes the gate.
 */
public class RAGService {

	/** Base threshold decided under D-RN05-01; production overrides it with the calibrated vector-store value. */
	public static final double DEFAULT_RELEVANCE_THRESHOLD = 0.65d;

	private final EmbeddingProvider embeddingProvider;

	private final VectorStore vectorStore;

	private final LLMRouter llmRouter;

	private final GroundedAnswerDecoder answerDecoder;

	private final CorpusIndex corpusIndex;

	private final double relevanceThreshold;

	public RAGService(EmbeddingProvider embeddingProvider, VectorStore vectorStore, LLMRouter llmRouter,
			GroundedAnswerDecoder answerDecoder) {
		this(embeddingProvider, vectorStore, llmRouter, answerDecoder, new CorpusIndex(), DEFAULT_RELEVANCE_THRESHOLD);
	}

	public RAGService(EmbeddingProvider embeddingProvider, VectorStore vectorStore, LLMRouter llmRouter,
			GroundedAnswerDecoder answerDecoder, double relevanceThreshold) {
		this(embeddingProvider, vectorStore, llmRouter, answerDecoder, new CorpusIndex(), relevanceThreshold);
	}

	public RAGService(EmbeddingProvider embeddingProvider, VectorStore vectorStore, LLMRouter llmRouter,
			GroundedAnswerDecoder answerDecoder, CorpusIndex corpusIndex, double relevanceThreshold) {
		this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
		this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
		this.llmRouter = Objects.requireNonNull(llmRouter, "llmRouter");
		this.answerDecoder = Objects.requireNonNull(answerDecoder, "answerDecoder");
		this.corpusIndex = Objects.requireNonNull(corpusIndex, "corpusIndex");
		if (!Double.isFinite(relevanceThreshold) || relevanceThreshold < 0.0d || relevanceThreshold > 1.0d) {
			throw new IllegalArgumentException("relevanceThreshold must be between 0 and 1");
		}
		this.relevanceThreshold = relevanceThreshold;
	}

	/** Compatibility constructor for a single provider; production wiring uses the resilient router. */
	public RAGService(EmbeddingProvider embeddingProvider, VectorStore vectorStore, LLMProvider llmProvider,
			GroundedAnswerDecoder answerDecoder) {
		this(embeddingProvider, vectorStore, llmProvider, answerDecoder, new CorpusIndex(),
				DEFAULT_RELEVANCE_THRESHOLD);
	}

	public RAGService(EmbeddingProvider embeddingProvider, VectorStore vectorStore, LLMProvider llmProvider,
			GroundedAnswerDecoder answerDecoder, CorpusIndex corpusIndex) {
		this(embeddingProvider, vectorStore, llmProvider, answerDecoder, corpusIndex, DEFAULT_RELEVANCE_THRESHOLD);
	}

	public RAGService(EmbeddingProvider embeddingProvider, VectorStore vectorStore, LLMProvider llmProvider,
			GroundedAnswerDecoder answerDecoder, CorpusIndex corpusIndex, double relevanceThreshold) {
		this(embeddingProvider, vectorStore, singleProviderRouter(llmProvider), answerDecoder, corpusIndex,
				relevanceThreshold);
	}

	private static LLMRouter singleProviderRouter(LLMProvider llmProvider) {
		Objects.requireNonNull(llmProvider, "llmProvider");
		return (question, evidence) -> llmProvider.generate(question, evidence)
				.map(answer -> new LLMCompletion("single", "unknown", answer));
	}

	public Mono<RAGResponse> answer(PromptRequest request) {
		double threshold = request.relevanceThreshold() != null ? request.relevanceThreshold() : relevanceThreshold;
		Mono<float[]> queryEmbedding = embeddingProvider instanceof TaskAwareEmbeddingProvider aware
				? aware.embedQuery(request.question()) : embeddingProvider.embed(request.question());
		return queryEmbedding
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
								// G-04: strict JSON; a parse error is a direct rejection, never a regeneration.
								var answer = answerDecoder.decode(completion.answer());
								// G-03: corpus index AND retrieved set; no DocumentoClinico read per request.
								List<Citation> citations = CitationValidator.resolve(answer, relevant, corpusIndex);
								List<EvidenceFragment> cited = citations.stream()
										.map(citation -> relevant.get(citation.position() - 1))
										.toList();
								return new RAGResponse(answer.respuesta(), cited, completion.usedModel());
							});
				});
	}

}
