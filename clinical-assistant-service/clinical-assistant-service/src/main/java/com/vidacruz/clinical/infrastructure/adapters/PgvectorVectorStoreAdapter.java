package com.vidacruz.clinical.infrastructure.adapters;

import java.util.List;
import java.util.UUID;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.ports.VectorStore;
import com.vidacruz.clinical.infrastructure.db.VectorRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Reactive facade over the blocking JDBC {@link VectorRepository} (ADR-04, option A). Every call runs on the injected
 * bounded scheduler, so the Netty event loop never blocks (NFR-01). When the scheduler's queue is full, subscription
 * fails fast with a {@code RejectedExecutionException} instead of queueing without limit.
 */
public class PgvectorVectorStoreAdapter implements VectorStore {

	private final VectorRepository repository;

	private final Scheduler jdbcScheduler;

	public PgvectorVectorStoreAdapter(VectorRepository repository, Scheduler jdbcScheduler) {
		this.repository = repository;
		this.jdbcScheduler = jdbcScheduler;
	}

	@Override
	public Mono<EvidenceFragment> save(EvidenceFragment fragment) {
		return Mono.fromCallable(() -> repository.upsert(fragment)).subscribeOn(jdbcScheduler);
	}

	@Override
	public Mono<EvidenceFragment> findById(UUID id) {
		// A null from the callable completes the Mono empty.
		return Mono.fromCallable(() -> repository.findById(id).orElse(null)).subscribeOn(jdbcScheduler);
	}

	@Override
	public Mono<Boolean> deleteById(UUID id) {
		return Mono.fromCallable(() -> repository.deleteById(id)).subscribeOn(jdbcScheduler);
	}

	@Override
	public Mono<List<EvidenceFragment>> similaritySearch(float[] queryEmbedding, int topK) {
		return Mono.fromCallable(() -> repository.searchSimilar(queryEmbedding, topK)).subscribeOn(jdbcScheduler);
	}

}
