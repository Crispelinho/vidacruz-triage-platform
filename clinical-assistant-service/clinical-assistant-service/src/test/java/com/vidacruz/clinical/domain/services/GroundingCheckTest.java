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

	@Test
	void defaultPolicyRejectsAnAnswerWithoutMarkers() {
		GroundingCheck check = new GroundingCheck(GroundingCheck.Policy.defaults());

		assertThatThrownBy(() -> check.verify(answer("Administrar adrenalina 1 mg."), CITATIONS, EVIDENCE))
				.isInstanceOf(InvalidCitationException.class);
	}

	@Test
	void defaultPolicyAcceptsAnAnswerWithAMarker() {
		GroundingCheck check = new GroundingCheck(GroundingCheck.Policy.defaults());

		assertThatCode(() -> check.verify(answer("Administrar adrenalina 1 mg [1]."), CITATIONS, EVIDENCE))
				.doesNotThrowAnyException();
	}

	@Test
	void numericCheckRejectsADoseThatIsNotInTheCitedEvidence() {
		// The M-1 exploit: a real, retrieved citation laundering a dose the protocol never states.
		GroundingCheck check = new GroundingCheck(new GroundingCheck.Policy(true, 0.0d, true));

		assertThatThrownBy(() -> check.verify(answer("Administrar adrenalina 10 mg IV [1]."), CITATIONS, EVIDENCE))
				.isInstanceOf(InvalidCitationException.class);
	}

	@Test
	void numericCheckAcceptsNumbersPresentInTheCitedEvidenceAndIgnoresMarkers() {
		GroundingCheck check = new GroundingCheck(new GroundingCheck.Policy(true, 0.0d, true));

		assertThatCode(() -> check.verify(answer("Adrenalina 1 mg cada 3 a 5 minutos [1]."), CITATIONS, EVIDENCE))
				.doesNotThrowAnyException();
	}

	@Test
	void lexicalOverlapRejectsAnAnswerUnrelatedToTheCitedEvidence() {
		GroundingCheck check = new GroundingCheck(new GroundingCheck.Policy(true, 0.3d, false));

		assertThatThrownBy(() -> check.verify(answer("Dar de alta sin electrocardiograma ni seguimiento [1]."),
				CITATIONS, EVIDENCE)).isInstanceOf(InvalidCitationException.class);
	}

	@Test
	void lexicalOverlapIgnoresCaseAndDiacritics() {
		GroundingCheck check = new GroundingCheck(new GroundingCheck.Policy(true, 0.5d, false));

		assertThatCode(() -> check.verify(answer("ADRENALINA intravenosa durante la reanimacion [1]."), CITATIONS,
				EVIDENCE)).doesNotThrowAnyException();
	}

	@Test
	void disabledPolicyAcceptsAnything() {
		GroundingCheck check = new GroundingCheck(GroundingCheck.Policy.disabled());

		assertThatCode(() -> check.verify(answer("texto libre 99"), CITATIONS, EVIDENCE)).doesNotThrowAnyException();
	}

	@Test
	void overlapOutsideTheUnitIntervalIsRejected() {
		assertThatThrownBy(() -> new GroundingCheck.Policy(true, 1.5d, false))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
