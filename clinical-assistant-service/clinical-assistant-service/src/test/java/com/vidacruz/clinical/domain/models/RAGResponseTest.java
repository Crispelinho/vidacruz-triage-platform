package com.vidacruz.clinical.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RAGResponseTest {

	private static EvidenceFragment fragment() {
		return EvidenceFragment.create(UUID.randomUUID(), 0, "PROTO-001", "Medidas iniciales", "texto",
				new float[] { 1f });
	}

	@Test
	void noEvidenceIsTheFixedNoticeWithoutSources() {
		RAGResponse response = RAGResponse.noEvidence();

		assertThat(response.answer()).isEqualTo("no existe protocolo disponible");
		assertThat(response.sources()).isEmpty();
		assertThat(response.hasEvidence()).isFalse();
	}

	@Test
	void sourcesAreCopiedDefensively() {
		List<EvidenceFragment> sources = new ArrayList<>(List.of(fragment()));
		RAGResponse response = new RAGResponse("respuesta", sources);

		sources.clear();

		assertThat(response.sources()).hasSize(1);
		assertThat(response.hasEvidence()).isTrue();
		assertThatThrownBy(() -> response.sources().add(fragment())).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsBlankAnswerAndNullSources() {
		assertThatThrownBy(() -> new RAGResponse(" ", List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RAGResponse("respuesta", null)).isInstanceOf(NullPointerException.class);
	}

}
