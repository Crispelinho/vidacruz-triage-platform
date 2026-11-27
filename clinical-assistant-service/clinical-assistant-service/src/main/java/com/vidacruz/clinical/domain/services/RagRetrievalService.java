package com.vidacruz.clinical.domain.services;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.vidacruz.clinical.domain.exceptions.InvalidCorpusRequestException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.RetrievedChunk;
import com.vidacruz.clinical.domain.models.ScoredEvidenceFragment;
import com.vidacruz.clinical.domain.ports.ClinicalDocumentRepository;
import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.VectorStore;

import reactor.core.publisher.Mono;

/**
 * Corpus retrieval behind the RN-05 anti-hallucination gate: only chunks whose cosine similarity reaches the
 * threshold are returned. When none does, the result is an empty list and callers must not fabricate an answer.
 *
 * <p>Results come back from the store closest first, so filtering the top {@value #TOP_K} is the same as taking the
 * top {@value #TOP_K} above the threshold. Ranking is plain cosine similarity, nothing more.
 */
public class RagRetrievalService {

	public static final double DEFAULT_THRESHOLD = 0.75d;

	public static final int TOP_K = 5;

	public static final int MAX_QUERY_LENGTH = 2000;

	private final EmbeddingProvider embeddingProvider;

	private final VectorStore vectorStore;

	private final ClinicalDocumentRepository documents;

	public RagRetrievalService(EmbeddingProvider embeddingProvider, VectorStore vectorStore,
			ClinicalDocumentRepository documents) {
		this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
		this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
		this.documents = Objects.requireNonNull(documents, "documents");
	}

	public Mono<List<RetrievedChunk>> retrieve(String query) {
		return retrieve(query, DEFAULT_THRESHOLD);
	}

	public Mono<List<RetrievedChunk>> retrieve(String query, double threshold) {
		return Mono.defer(() -> {
			validate(query, threshold);
			return embeddingProvider.embed(query)
					.flatMap(embedding -> vectorStore.similaritySearchWithScores(embedding, TOP_K))
					.flatMap(scored -> {
						// RN-05 gate: below-threshold chunks never leave this method.
						List<ScoredEvidenceFragment> passed = scored.stream()
								.filter(result -> result.score() >= threshold)
								.toList();
						if (passed.isEmpty()) {
							return Mono.just(List.<RetrievedChunk>of());
						}
						List<UUID> ids = passed.stream()
								.map(result -> result.fragment().documentId())
								.distinct()
								.toList();
						return documents.findTitles(ids)
								.map(titles -> passed.stream().map(result -> toChunk(result, titles)).toList());
					});
		});
	}

	private static RetrievedChunk toChunk(ScoredEvidenceFragment result, Map<UUID, String> titles) {
		EvidenceFragment fragment = result.fragment();
		String source = titles.get(fragment.documentId());
		if (source == null) {
			source = fragment.protocol() != null ? fragment.protocol() : fragment.documentId().toString();
		}
		return new RetrievedChunk(fragment.documentId(), fragment.chunkIndex(), fragment.content(), result.score(),
				source);
	}

	private static void validate(String query, double threshold) {
		if (query == null || query.isBlank()) {
			throw new InvalidCorpusRequestException("query must not be blank");
		}
		if (query.length() > MAX_QUERY_LENGTH) {
			throw new InvalidCorpusRequestException("query must be at most " + MAX_QUERY_LENGTH + " characters");
		}
		if (!Double.isFinite(threshold) || threshold < 0.0d || threshold > 1.0d) {
			throw new InvalidCorpusRequestException("threshold must be between 0 and 1");
		}
	}

}
