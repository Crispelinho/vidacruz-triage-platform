package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class FixtureEmbeddingAdapterTest {

	private final FixtureEmbeddingAdapter adapter = new FixtureEmbeddingAdapter(768);

	@Test
	void sameTextGivesTheSameVector() {
		float[] first = adapter.embed("crisis hipertensiva").block();
		float[] second = adapter.embed("crisis hipertensiva").block();

		assertThat(first).hasSize(768).containsExactly(second);
	}

	@Test
	void differentTextGivesADifferentVector() {
		assertThat(adapter.embed("a").block()).isNotEqualTo(adapter.embed("b").block());
	}

	@Test
	void vectorIsNormalizedToUnitLength() {
		float[] vector = adapter.embed("texto").block();
		double norm = 0;
		for (float value : vector) {
			norm += value * value;
		}

		assertThat(Math.sqrt(norm)).isCloseTo(1.0, within(1e-5));
	}

	@Test
	void rejectsNonPositiveDimensions() {
		assertThatThrownBy(() -> new FixtureEmbeddingAdapter(0)).isInstanceOf(IllegalArgumentException.class);
	}

}
