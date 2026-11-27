package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class LexicalEmbeddingAdapterTest {

	private final LexicalEmbeddingAdapter adapter = new LexicalEmbeddingAdapter(768);

	@Test
	void accentAndInflectionVariantsShareTheSameStem() {
		assertThat(cosine("hipertensión", "hipertensiva")).isCloseTo(1.0d, Offset.offset(1e-9));
		assertThat(cosine("primer antibiótico", "primera antibiotico")).isCloseTo(1.0d, Offset.offset(1e-9));
	}

	@Test
	void stopwordsOnlyProduceAZeroVector() {
		assertThat(adapter.embed("el la los de del y o").block()).containsOnly(0f);
	}

	@Test
	void dimensionsMustBePositive() {
		assertThatThrownBy(() -> new LexicalEmbeddingAdapter(0)).isInstanceOf(IllegalArgumentException.class);
	}

	private double cosine(String left, String right) {
		float[] a = adapter.embed(left).block();
		float[] b = adapter.embed(right).block();
		double dot = 0d;
		double normA = 0d;
		double normB = 0d;
		for (int i = 0; i < a.length; i++) {
			dot += (double) a[i] * b[i];
			normA += (double) a[i] * a[i];
			normB += (double) b[i] * b[i];
		}
		return dot / (Math.sqrt(normA) * Math.sqrt(normB));
	}

}
