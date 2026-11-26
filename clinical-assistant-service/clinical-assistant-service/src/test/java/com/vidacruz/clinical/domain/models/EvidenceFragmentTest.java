package com.vidacruz.clinical.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class EvidenceFragmentTest {

	private static final UUID DOCUMENT = UUID.randomUUID();

	@Test
	void createAssignsRandomIdAndKeepsFields() {
		EvidenceFragment fragment = EvidenceFragment.create(DOCUMENT, 2, "PROTO-001", "Medidas iniciales", "texto",
				new float[] { 0.1f, 0.2f });

		assertThat(fragment.id()).isNotNull();
		assertThat(fragment.documentId()).isEqualTo(DOCUMENT);
		assertThat(fragment.chunkIndex()).isEqualTo(2);
		assertThat(fragment.protocol()).isEqualTo("PROTO-001");
		assertThat(fragment.section()).isEqualTo("Medidas iniciales");
	}

	@Test
	void embeddingIsDefensivelyCopiedOnTheWayInAndOut() {
		float[] source = { 1f, 2f };
		EvidenceFragment fragment = EvidenceFragment.create(DOCUMENT, 0, null, null, "texto", source);

		source[0] = 99f;
		fragment.embedding()[1] = 99f;

		assertThat(fragment.embedding()).containsExactly(1f, 2f);
	}

	@Test
	void equalityComparesEmbeddingByValue() {
		UUID id = UUID.randomUUID();
		EvidenceFragment a = new EvidenceFragment(id, DOCUMENT, 0, null, null, "texto", new float[] { 1f, 2f });
		EvidenceFragment b = new EvidenceFragment(id, DOCUMENT, 0, null, null, "texto", new float[] { 1f, 2f });
		EvidenceFragment c = new EvidenceFragment(id, DOCUMENT, 0, null, null, "texto", new float[] { 1f, 3f });

		assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(c);
	}

	@Test
	void rejectsInvalidState() {
		float[] ok = { 1f };
		assertThatThrownBy(() -> EvidenceFragment.create(DOCUMENT, -1, null, null, "texto", ok))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("chunkIndex");
		assertThatThrownBy(() -> EvidenceFragment.create(DOCUMENT, 0, null, null, " ", ok))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("content");
		assertThatThrownBy(() -> EvidenceFragment.create(DOCUMENT, 0, null, null, "texto", new float[0]))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("embedding");
		assertThatThrownBy(() -> EvidenceFragment.create(DOCUMENT, 0, null, null, "texto", new float[] { Float.NaN }))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("finite");
		assertThatThrownBy(() -> new EvidenceFragment(null, DOCUMENT, 0, null, null, "texto", ok))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void toStringOmitsContentAndEmbedding() {
		EvidenceFragment fragment = EvidenceFragment.create(DOCUMENT, 0, "PROTO-001", null, "texto clinico",
				new float[] { 0.5f });

		assertThat(fragment.toString()).doesNotContain("texto clinico").contains("dimensions=1");
	}

}
