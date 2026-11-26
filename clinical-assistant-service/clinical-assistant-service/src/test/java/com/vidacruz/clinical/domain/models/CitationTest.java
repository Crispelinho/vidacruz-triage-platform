package com.vidacruz.clinical.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CitationTest {

	private static EvidenceFragment fragment(String protocol, String section, int chunk) {
		return EvidenceFragment.create(UUID.randomUUID(), chunk, protocol, section, "texto", new float[] { 1f });
	}

	@Test
	void ofCopiesProvenanceFromTheRealFragment() {
		EvidenceFragment fragment = fragment("PROTO-001", "Medidas iniciales", 3);

		Citation citation = Citation.of(2, fragment);

		assertThat(citation.position()).isEqualTo(2);
		assertThat(citation.fragmentId()).isEqualTo(fragment.id());
		assertThat(citation.documentId()).isEqualTo(fragment.documentId());
		assertThat(citation.chunkIndex()).isEqualTo(3);
		assertThat(citation.protocol()).isEqualTo("PROTO-001");
		assertThat(citation.section()).isEqualTo("Medidas iniciales");
	}

	@Test
	void fromEvidenceNumbersFromOneInListOrder() {
		EvidenceFragment first = fragment("PROTO-001", "A", 0);
		EvidenceFragment second = fragment("PROTO-002", "B", 1);

		List<Citation> citations = Citation.fromEvidence(List.of(first, second));

		assertThat(citations).extracting(Citation::position).containsExactly(1, 2);
		assertThat(citations).extracting(Citation::fragmentId).containsExactly(first.id(), second.id());
	}

	@Test
	void fromEmptyEvidenceIsEmpty() {
		assertThat(Citation.fromEvidence(List.of())).isEmpty();
	}

	@Test
	void rejectsInvalidInvariants() {
		UUID id = UUID.randomUUID();
		assertThatThrownBy(() -> new Citation(0, id, id, 0, null, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Citation(1, null, id, 0, null, null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new Citation(1, id, null, 0, null, null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new Citation(1, id, id, -1, null, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Citation.of(1, null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void ragResponseCitationsAreDerivedFromItsSources() {
		EvidenceFragment first = fragment("PROTO-001", "A", 0);
		RAGResponse response = new RAGResponse("respuesta [1]", List.of(first));

		assertThat(response.citations()).containsExactly(Citation.of(1, first));
		assertThat(RAGResponse.noEvidence().citations()).isEmpty();
	}

}
