package com.vidacruz.clinical.domain.ports;

import java.util.List;
import java.util.UUID;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.ScoredEvidenceFragment;

import reactor.core.publisher.Mono;

/**
 * Evidence storage and similarity search. The signatures are reactive on purpose: implementations backed by blocking
 * JDBC must isolate the call off the event loop (ADR-04, NFR-01).
 */
public interface VectorStore {

	/** Inserts or updates the fragment by id. */
	Mono<EvidenceFragment> save(EvidenceFragment fragment);

	/** Emits the fragment, or completes empty when it does not exist. */
	Mono<EvidenceFragment> findById(UUID id);

	/** Emits {@code true} when a fragment was deleted, {@code false} when it did not exist. */
	Mono<Boolean> deleteById(UUID id);

	/** Nearest fragments to the query embedding, closest first. Empty list when the corpus is empty. */
	Mono<List<EvidenceFragment>> similaritySearch(float[] queryEmbedding, int topK);

	/**
	 * Nearest fragments with relevance scores in [0, 1], closest first.
	 * Default keeps backward compatibility for stores that do not expose scores.
	 */
	default Mono<List<ScoredEvidenceFragment>> similaritySearchWithScores(float[] queryEmbedding, int topK) {
		return similaritySearch(queryEmbedding, topK)
				.map(results -> results.stream().map(fragment -> new ScoredEvidenceFragment(fragment, 1.0d)).toList());
	}

}
