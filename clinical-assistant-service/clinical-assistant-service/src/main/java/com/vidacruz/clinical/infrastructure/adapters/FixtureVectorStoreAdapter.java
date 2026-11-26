package com.vidacruz.clinical.infrastructure.adapters;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.ScoredEvidenceFragment;
import com.vidacruz.clinical.domain.ports.ScoredVectorStore;
import com.vidacruz.clinical.domain.ports.VectorStore;

import reactor.core.publisher.Mono;

/**
 * FIXTURE in-memory {@link VectorStore}: exact cosine ranking over a map, no database and no pgvector. It exists so
 * the RAG pipeline can run end to end without infrastructure. It is not evidence that retrieval works against
 * pgvector (that is covered by the Testcontainers tests) and it has no persistence: data is lost on restart.
 *
 * <p>Ties are broken by (documentId, chunkIndex) so rankings are deterministic.
 */
public class FixtureVectorStoreAdapter implements ScoredVectorStore, VectorStore {

	private final Map<UUID, EvidenceFragment> fragments = new ConcurrentHashMap<>();

	@Override
	public Mono<EvidenceFragment> save(EvidenceFragment fragment) {
		return Mono.fromSupplier(() -> {
			fragments.put(fragment.id(), fragment);
			return fragment;
		});
	}

	@Override
	public Mono<EvidenceFragment> findById(UUID id) {
		return Mono.fromSupplier(() -> fragments.get(id));
	}

	@Override
	public Mono<Boolean> deleteById(UUID id) {
		return Mono.fromSupplier(() -> fragments.remove(id) != null);
	}

	@Override
	public Mono<List<EvidenceFragment>> similaritySearch(float[] queryEmbedding, int topK) {
		return similaritySearchWithScores(queryEmbedding, topK)
				.map(results -> results.stream().map(ScoredEvidenceFragment::fragment).toList());
	}

	@Override
	public Mono<List<ScoredEvidenceFragment>> similaritySearchWithScores(float[] queryEmbedding, int topK) {
		return Mono.fromSupplier(() -> {
			if (topK < 1) {
				throw new IllegalArgumentException("topK must be positive");
			}
			Comparator<ScoredEvidenceFragment> byScore = Comparator
					.comparingDouble(ScoredEvidenceFragment::score).reversed();
			return fragments.values().stream()
					.filter(f -> f.embedding().length == queryEmbedding.length)
					.map(f -> new ScoredEvidenceFragment(f, normalizedCosine(queryEmbedding, f.embedding())))
					.sorted(byScore.thenComparing(f -> f.fragment().documentId())
							.thenComparingInt(f -> f.fragment().chunkIndex()))
					.limit(topK)
					.toList();
		});
	}

	private static double normalizedCosine(float[] a, float[] b) {
		double dot = 0;
		double normA = 0;
		double normB = 0;
		for (int i = 0; i < a.length; i++) {
			dot += (double) a[i] * b[i];
			normA += (double) a[i] * a[i];
			normB += (double) b[i] * b[i];
		}
		double cosine = normA == 0 || normB == 0 ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
		if (!Double.isFinite(cosine)) {
			return 0d;
		}
		return Math.max(0d, Math.min(1d, cosine));
	}

}
