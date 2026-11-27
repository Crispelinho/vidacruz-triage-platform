package com.vidacruz.clinical.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.exceptions.InvalidCitationException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.GroundedAnswer;
import com.vidacruz.clinical.domain.models.ProtocolSection;

class GroundedCitationValidatorTest {

	private final EvidenceFragment fragment = EvidenceFragment.create(UUID.randomUUID(), 0, "PROTO-001",
			"Medidas iniciales", "texto", new float[] { 1f });

	private GroundedAnswer answer(String protocol, String section, String text) {
		return new GroundedAnswer(text, List.of(ProtocolSection.of(protocol, section)));
	}

	@Test
	void acceptsSourcePresentInIndexAndRetrievedSet() {
		CorpusIndex index = new CorpusIndex();
		index.add(ProtocolSection.from(fragment));

		assertThat(CitationValidator.resolve(answer("PROTO-001", "Medidas iniciales", "texto [1]"),
				List.of(fragment), index)).hasSize(1);
	}

	@Test
	void rejectsSourceMissingFromIndex() {
		CorpusIndex index = new CorpusIndex();

		assertThatThrownBy(() -> CitationValidator.resolve(
				answer("PROTO-001", "Medidas iniciales", "texto"), List.of(fragment), index))
				.isInstanceOf(InvalidCitationException.class);
	}

	@Test
	void rejectsSourceMissingFromRetrievedSet() {
		CorpusIndex index = new CorpusIndex();
		index.add(ProtocolSection.from(fragment));
		EvidenceFragment other = EvidenceFragment.create(UUID.randomUUID(), 1, "PROTO-002", "Seguimiento", "texto",
				new float[] { 1f });

		assertThatThrownBy(() -> CitationValidator.resolve(answer("PROTO-002", "Seguimiento", "texto"),
				List.of(fragment), index)).isInstanceOf(InvalidCitationException.class);
	}

	@Test
	void rejectsPositionalMarkerOutsideTheValidatedSources() {
		CorpusIndex index = new CorpusIndex();
		index.add(ProtocolSection.from(fragment));

		assertThatThrownBy(() -> CitationValidator.resolve(
				answer("PROTO-001", "Medidas iniciales", "texto [7]"), List.of(fragment), index))
				.isInstanceOf(InvalidCitationException.class);
	}

}
