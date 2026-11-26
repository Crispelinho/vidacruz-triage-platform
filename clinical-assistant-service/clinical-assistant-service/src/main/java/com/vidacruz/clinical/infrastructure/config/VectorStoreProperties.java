package com.vidacruz.clinical.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Vector store settings.
 *
 * @param dimensions         embedding size; must match {@code vector(N)} in the Flyway migration.
 * @param relevanceThreshold minimum relevance [0,1] required before calling LLM (RN-05.3/G-02).
 * @param scheduler          bounded scheduler isolating blocking JDBC calls from the event loop (ADR-04, NFR-01)
 */
@ConfigurationProperties(prefix = "clinical.vector-store")
public record VectorStoreProperties(@DefaultValue("768") int dimensions,
		@DefaultValue("0.0") double relevanceThreshold, @DefaultValue Scheduler scheduler) {

	public VectorStoreProperties {
		if (dimensions < 1) {
			throw new IllegalArgumentException("clinical.vector-store.dimensions must be positive");
		}
		if (!Double.isFinite(relevanceThreshold) || relevanceThreshold < 0.0d || relevanceThreshold > 1.0d) {
			throw new IllegalArgumentException("clinical.vector-store.relevance-threshold must be between 0 and 1");
		}
	}

	/**
	 * @param threadCap     maximum JDBC threads; size the JDBC pool to match
	 * @param queuedTaskCap maximum queued retrievals; beyond it calls fail fast instead of queueing without limit
	 */
	public record Scheduler(@DefaultValue("4") int threadCap, @DefaultValue("100") int queuedTaskCap) {

		public Scheduler {
			if (threadCap < 1 || queuedTaskCap < 0) {
				throw new IllegalArgumentException("scheduler thread-cap must be >= 1 and queued-task-cap >= 0");
			}
		}

	}

}
