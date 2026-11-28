package com.vidacruz.clinical.domain.services;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.exceptions.InvalidCitationException;
import com.vidacruz.clinical.domain.models.Citation;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.GroundedAnswer;
import com.vidacruz.clinical.domain.models.ProtocolSection;

class GroundingCheckTest {

	private static final EvidenceFragment ADRENALINE = EvidenceFragment.create(UUID.randomUUID(), 0, "PROTO-001",
			"Paro cardiaco", "Administrar adrenalina 1 mg intravenosa cada 3 a 5 minutos durante la reanimación.",
			new float[] { 1f });

	private static final List<EvidenceFragment> EVIDENCE = List.of(ADRENALINE);

	private static final List<Citation> CITATIONS = List.of(Citation.of(1, ADRENALINE));

	private static GroundedAnswer answer(String text) {
		return new GroundedAnswer(text, List.of(ProtocolSection.from(ADRENALINE)));
	}

	private static void accepts(GroundingCheck.Policy policy, String text) {
		assertThatCode(() -> new GroundingCheck(policy).verify(answer(text), CITATIONS, EVIDENCE))
				.doesNotThrowAnyException();
	}

	private static void rejects(GroundingCheck.Policy policy, String text) {
		assertThatThrownBy(() -> new GroundingCheck(policy).verify(answer(text), CITATIONS, EVIDENCE))
				.isInstanceOf(InvalidCitationException.class);
	}

	@Test
	void defaultPolicyAcceptsAnAnswerWithAMarker() {
		accepts(GroundingCheck.Policy.defaults(),
				"Se debe administrar adrenalina intravenosa durante la reanimación [1].");
	}

	@Test
	void defaultPolicyAcceptsAMarkerlessAnswerThatUsesTheEvidenceVocabulary() {
		// Real providers do not always emit [n]; a faithful paraphrase must not become the no-protocol notice.
		accepts(GroundingCheck.Policy.defaults(), "Se debe administrar adrenalina intravenosa durante la reanimación.");
	}

	@Test
	void defaultPolicyRejectsAMarkerlessAnswerUnrelatedToTheCitedEvidence() {
		// The M-1 laundering case: a real, retrieved citation attached to a claim the source does not make.
		rejects(GroundingCheck.Policy.defaults(), "Dar de alta sin electrocardiograma ni seguimiento.");
	}

	@Test
	void defaultPolicyRejectsAMarkerlessAnswerMadeOnlyOfFunctionWords() {
		rejects(GroundingCheck.Policy.defaults(), "Según la evidencia, para el paciente.");
	}

	@Test
	void strictMarkerPolicyRejectsAnyAnswerWithoutAMarker() {
		rejects(new GroundingCheck.Policy(true, 0.0d, 0.0d, false), "Administrar adrenalina 1 mg.");
	}

	@Test
	void numericCheckRejectsADoseThatIsNotInTheCitedEvidence() {
		rejects(new GroundingCheck.Policy(false, 0.0d, 0.0d, true), "Administrar adrenalina 10 mg IV [1].");
	}

	@Test
	void numericCheckAcceptsNumbersPresentInTheCitedEvidenceAndIgnoresMarkers() {
		accepts(new GroundingCheck.Policy(false, 0.0d, 0.0d, true), "Adrenalina 1 mg cada 3 a 5 minutos [1].");
	}

	@Test
	void baselineOverlapAppliesEvenWhenTheAnswerHasAMarker() {
		// A fabricated [n] must not launder text that shares no vocabulary with the cited fragment.
		rejects(new GroundingCheck.Policy(false, 0.2d, 0.2d, false),
				"Dar de alta sin electrocardiograma ni seguimiento [1].");
	}

	@Test
	void minLexicalOverlapAppliesEvenWhenTheAnswerHasAMarker() {
		rejects(new GroundingCheck.Policy(false, 0.0d, 0.3d, false),
				"Dar de alta sin electrocardiograma ni seguimiento [1].");
	}

	@Test
	void lexicalOverlapIgnoresCaseAndDiacritics() {
		accepts(new GroundingCheck.Policy(false, 0.0d, 0.5d, false),
				"ADRENALINA intravenosa durante la reanimacion [1].");
	}

	@Test
	void disabledPolicyAcceptsAnything() {
		accepts(GroundingCheck.Policy.disabled(), "texto libre 99");
	}

	@Test
	void overlapOutsideTheUnitIntervalIsRejected() {
		assertThatThrownBy(() -> new GroundingCheck.Policy(false, 1.5d, 0.0d, false))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new GroundingCheck.Policy(false, 0.2d, -0.1d, false))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
