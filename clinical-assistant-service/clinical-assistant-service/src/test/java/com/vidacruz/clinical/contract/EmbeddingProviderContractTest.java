package com.vidacruz.clinical.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.infrastructure.adapters.FixtureEmbeddingAdapter;

/**
 * Port-level expectations for {@link EmbeddingProvider}, checked on the fixture. A real provider (ADR-05) must pass
 * the same: finite values, stable dimension, deterministic for equal text.
 */
class EmbeddingProviderContractTest {

	private final EmbeddingProvider provider = new FixtureEmbeddingAdapter(768);

	@Test
	void everyEmbeddingHasTheSameDimensionAndOnlyFiniteValues() {
		for (String text : new String[] { "a", "crisis hipertensiva", "x".repeat(2000) }) {
			float[] embedding = provider.embed(text).block();

			assertThat(embedding).hasSize(768);
			for (float value : embedding) {
				assertThat(Float.isFinite(value)).isTrue();
			}
		}
	}

	@Test
	void equalTextIsDeterministic() {
		assertThat(provider.embed("texto").block()).containsExactly(provider.embed("texto").block());
	}

}
