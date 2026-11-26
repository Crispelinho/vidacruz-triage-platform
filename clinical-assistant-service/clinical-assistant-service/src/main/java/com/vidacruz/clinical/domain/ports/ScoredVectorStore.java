package com.vidacruz.clinical.domain.ports;

import java.util.List;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.ScoredEvidenceFragment;

import reactor.core.publisher.Mono;

/**
 * Optional vector-store capability that exposes relevance scores.
 * Implementations should return scores in [0, 1], closest first.
 */
public interface ScoredVectorStore extends VectorStore {

	Mono<List<ScoredEvidenceFragment>> similaritySearchWithScores(float[] queryEmbedding, int topK);

	@Override
	default Mono<List<EvidenceFragment>> similaritySearch(float[] queryEmbedding, int topK) {
		return similaritySearchWithScores(queryEmbedding, topK)
				.map(results -> results.stream().map(ScoredEvidenceFragment::fragment).toList());
	}

}
