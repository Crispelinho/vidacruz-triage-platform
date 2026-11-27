package com.vidacruz.clinical.infrastructure.adapters;

import java.util.Random;

import com.vidacruz.clinical.domain.ports.EmbeddingProvider;

import reactor.core.publisher.Mono;

/**
 * Deterministic fixture embedding: the same text always yields the same unit vector. It carries no semantics, so it
 * only supports wiring and plumbing tests. Replaced by a real embedding model once ADR-05 decides it (G-01).
 */
public class FixtureEmbeddingAdapter implements EmbeddingProvider {

	private final int dimensions;

	public FixtureEmbeddingAdapter(int dimensions) {
		if (dimensions < 1) {
			throw new IllegalArgumentException("dimensions must be positive");
		}
		this.dimensions = dimensions;
	}

	@Override
	public Mono<float[]> embed(String text) {
		return Mono.fromSupplier(() -> vectorFor(text));
	}

	private float[] vectorFor(String text) {
		// String.hashCode is specified by the JLS, so the seed is stable across JVMs.
		Random random = new Random(text.hashCode());
		float[] vector = new float[dimensions];
		double norm = 0;
		for (int i = 0; i < dimensions; i++) {
			vector[i] = (float) random.nextGaussian();
			norm += vector[i] * vector[i];
		}
		float scale = (float) (1.0 / Math.sqrt(norm));
		for (int i = 0; i < dimensions; i++) {
			vector[i] *= scale;
		}
		return vector;
	}

}
