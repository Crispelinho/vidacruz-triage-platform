package com.vidacruz.clinical.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.vidacruz.clinical.domain.exceptions.InvalidCitationException;
import com.vidacruz.clinical.domain.models.Citation;
import com.vidacruz.clinical.domain.models.EvidenceFragment;

class CitationValidatorTest {

	private static final List<EvidenceFragment> EVIDENCE = List.of(
			EvidenceFragment.create(UUID.randomUUID(), 0, "PROTO-001", "A", "uno", new float[] { 1f }),
			EvidenceFragment.create(UUID.randomUUID(), 1, "PROTO-002", "B", "dos", new float[] { 1f }));

	@Test
	void answerWithoutMarkersIsRejected() {
		assertThatThrownBy(() -> CitationValidator.resolve("respuesta sin marcadores", EVIDENCE))
				.isInstanceOf(InvalidCitationException.class)
				.hasMessage("answer has no valid citation marker");
	}

	@Test
	void resolvesMarkersToTheRealFragmentsOrderedAndDistinct() {
		List<Citation> cited = CitationValidator.resolve("segun [2] y tambien [1], reiterado en [2]", EVIDENCE);

		assertThat(cited).extracting(Citation::position).containsExactly(1, 2);
		assertThat(cited.get(0).fragmentId()).isEqualTo(EVIDENCE.get(0).id());
		assertThat(cited.get(1).fragmentId()).isEqualTo(EVIDENCE.get(1).id());
	}

	@ParameterizedTest
	@ValueSource(strings = { "ver [3]", "ver [0]", "ver [1] y [99]" })
	void rejectsMarkersThatPointToNoRetrievedFragment(String answer) {
		assertThatThrownBy(() -> CitationValidator.resolve(answer, EVIDENCE))
				.isInstanceOf(InvalidCitationException.class)
				.hasMessageNotContaining("ver");
	}

	@Test
	void anyMarkerIsInvalidWhenThereIsNoEvidence() {
		assertThatThrownBy(() -> CitationValidator.resolve("ver [1]", List.of()))
				.isInstanceOf(InvalidCitationException.class);
	}

	@Test
	void bracketsThatAreNotPositiveIntegersAreNotValidMarkers() {
		assertThatThrownBy(() -> CitationValidator.resolve("nota [a] [] [1.5]", EVIDENCE))
				.isInstanceOf(InvalidCitationException.class);
	}

}
