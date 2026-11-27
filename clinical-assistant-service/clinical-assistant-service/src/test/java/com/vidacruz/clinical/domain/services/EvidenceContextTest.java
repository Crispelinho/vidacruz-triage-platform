package com.vidacruz.clinical.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.models.EvidenceFragment;

class EvidenceContextTest {

	@Test
	void numbersFragmentsFromOneWithProtocolSectionAndContent() {
		List<EvidenceFragment> evidence = List.of(
				EvidenceFragment.create(UUID.randomUUID(), 0, "PROTO-001", "Medidas", "texto uno", new float[] { 1f }),
				EvidenceFragment.create(UUID.randomUUID(), 1, null, null, "texto dos", new float[] { 1f }));

		String context = EvidenceContext.render(evidence);

		assertThat(context).startsWith("[1] PROTO-001 / Medidas\ntexto uno")
				.contains("[2] \ntexto dos")
				.doesNotEndWith("\n");
	}

	@Test
	void emptyEvidenceRendersEmptyContext() {
		assertThat(EvidenceContext.render(List.of())).isEmpty();
	}

}
