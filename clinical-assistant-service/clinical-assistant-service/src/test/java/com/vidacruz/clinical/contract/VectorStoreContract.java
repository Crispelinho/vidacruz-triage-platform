package com.vidacruz.clinical.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.ports.VectorStore;

/**
 * Behavior every {@link VectorStore} implementation must honor. Run against the in-memory fixture and the real
 * pgvector adapter, so the fixture cannot drift from the port's semantics.
 */
public abstract class VectorStoreContract {

	protected static final int DIMENSIONS = 768;

	protected abstract VectorStore store();

	/** Unit vector along one axis, leaning toward another; never the zero vector. */
	protected static float[] vector(int axis, int leanAxis, float lean) {
		float[] vector = new float[DIMENSIONS];
		vector[axis] = 1f;
		if (leanAxis >= 0) {
			vector[leanAxis] = lean;
		}
		return vector;
	}

	protected static EvidenceFragment fragment(String protocol, float[] embedding) {
		return EvidenceFragment.create(UUID.randomUUID(), 0, protocol, "Seccion", "texto " + protocol, embedding);
	}

	@Test
	void saveThenFindByIdRoundTrips() {
		EvidenceFragment saved = fragment("PROTO-001", vector(0, -1, 0));

		assertThat(store().save(saved).block()).isEqualTo(saved);
		assertThat(store().findById(saved.id()).block()).isEqualTo(saved);
	}

	@Test
	void findByIdOfUnknownIdCompletesEmpty() {
		assertThat(store().findById(UUID.randomUUID()).blockOptional()).isEmpty();
	}

	@Test
	void deleteByIdReportsWhetherSomethingWasDeleted() {
		EvidenceFragment saved = fragment("PROTO-001", vector(0, -1, 0));
		store().save(saved).block();

		assertThat(store().deleteById(saved.id()).block()).isTrue();
		assertThat(store().deleteById(saved.id()).block()).isFalse();
		assertThat(store().findById(saved.id()).blockOptional()).isEmpty();
	}

	@Test
	void similaritySearchOnAnEmptyCorpusIsAnEmptyList() {
		assertThat(store().similaritySearch(vector(0, -1, 0), 4).block()).isEmpty();
	}

	@Test
	void similaritySearchReturnsClosestFirstAndHonorsTopK() {
		EvidenceFragment exact = fragment("EXACT", vector(0, -1, 0));
		EvidenceFragment near = fragment("NEAR", vector(0, 1, 0.3f));
		EvidenceFragment far = fragment("FAR", vector(0, 1, 2f));
		EvidenceFragment orthogonal = fragment("ORTHOGONAL", vector(5, -1, 0));
		for (EvidenceFragment fragment : List.of(orthogonal, far, exact, near)) {
			store().save(fragment).block();
		}

		List<EvidenceFragment> top3 = store().similaritySearch(vector(0, -1, 0), 3).block();

		assertThat(top3).extracting(EvidenceFragment::protocol).containsExactly("EXACT", "NEAR", "FAR");
		assertThat(store().similaritySearch(vector(0, -1, 0), 1).block()).extracting(EvidenceFragment::protocol)
				.containsExactly("EXACT");
	}

	@Test
	void topKLargerThanTheCorpusReturnsEverything() {
		store().save(fragment("A", vector(0, -1, 0))).block();
		store().save(fragment("B", vector(1, -1, 0))).block();

		assertThat(store().similaritySearch(vector(0, -1, 0), 20).block()).hasSize(2);
	}

}
