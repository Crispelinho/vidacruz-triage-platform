package com.vidacruz.clinical.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Vector store settings.
 *
 * @param dimensions embedding size; must match {@code vector(N)} in the Flyway migration. Provisional (768) until
 *                   ADR-05 chooses the embedding model (G-01); changing it needs a new migration and a re-ingestion.
 * @param scheduler  bounded scheduler isolating blocking JDBC calls from the event loop (ADR-04, NFR-01)
 */
@ConfigurationProperties(prefix = "clinical.vector-store")
public record VectorStoreProperties(@DefaultValue("768") int dimensions, @DefaultValue Scheduler scheduler) {

	public VectorStoreProperties {
		if (dimensions < 1) {
			throw new IllegalArgumentException("clinical.vector-store.dimensions must be positive");
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
