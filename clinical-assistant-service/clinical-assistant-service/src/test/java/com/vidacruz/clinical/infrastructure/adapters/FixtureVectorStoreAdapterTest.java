package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.models.EvidenceFragment;

import reactor.test.StepVerifier;

/** Fixture-specific behavior; the shared port semantics live in {@code VectorStoreContract}. */
class FixtureVectorStoreAdapterTest {

	private final FixtureVectorStoreAdapter store = new FixtureVectorStoreAdapter();

	private static EvidenceFragment fragment(UUID documentId, int chunk, float... embedding) {
		return EvidenceFragment.create(documentId, chunk, "PROTO-001", "Seccion", "texto", embedding);
	}

	@Test
	void saveWithTheSameIdReplacesTheFragment() {
		EvidenceFragment original = fragment(UUID.randomUUID(), 0, 1f, 0f);
		EvidenceFragment updated = new EvidenceFragment(original.id(), original.documentId(), 0, "PROTO-002", "Otra",
				"texto nuevo", new float[] { 0f, 1f });

		store.save(original).block();
		store.save(updated).block();

		assertThat(store.findById(original.id()).block()).isEqualTo(updated);
		assertThat(store.similaritySearch(new float[] { 0f, 1f }, 10).block()).hasSize(1);
	}

	@Test
	void identicalScoresAreOrderedDeterministicallyByDocumentThenChunk() {
		UUID low = new UUID(0, 1);
		UUID high = new UUID(0, 2);
		EvidenceFragment a = fragment(high, 0, 1f, 0f);
		EvidenceFragment b = fragment(low, 1, 1f, 0f);
		EvidenceFragment c = fragment(low, 0, 1f, 0f);
		for (EvidenceFragment f : List.of(a, b, c)) {
			store.save(f).block();
		}

		assertThat(store.similaritySearch(new float[] { 1f, 0f }, 3).block()).containsExactly(c, b, a);
	}

	@Test
	void fragmentsWithAnotherDimensionAreNotCandidates() {
		store.save(fragment(UUID.randomUUID(), 0, 1f, 0f, 0f)).block();

		assertThat(store.similaritySearch(new float[] { 1f, 0f }, 4).block()).isEmpty();
	}

	@Test
	void zeroVectorQueryDoesNotFailAndScoresZero() {
		EvidenceFragment only = fragment(UUID.randomUUID(), 0, 1f, 0f);
		store.save(only).block();

		assertThat(store.similaritySearch(new float[] { 0f, 0f }, 4).block()).containsExactly(only);
	}

	@Test
	void nonPositiveTopKIsAnError() {
		StepVerifier.create(store.similaritySearch(new float[] { 1f }, 0))
				.expectError(IllegalArgumentException.class)
				.verify();
	}

}
