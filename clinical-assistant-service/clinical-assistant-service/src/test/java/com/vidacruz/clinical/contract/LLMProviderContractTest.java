package com.vidacruz.clinical.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.GroundedAnswer;
import com.vidacruz.clinical.domain.ports.LLMProvider;
import com.vidacruz.clinical.domain.services.CitationValidator;
import com.vidacruz.clinical.domain.services.CorpusIndex;
import com.vidacruz.clinical.infrastructure.adapters.JacksonGroundedAnswerDecoder;
import com.vidacruz.clinical.infrastructure.adapters.OllamaLLMAdapter;
import com.vidacruz.clinical.infrastructure.config.OllamaProperties;

import reactor.test.StepVerifier;

/**
 * Port-level expectations for {@link LLMProvider}, checked on the fixture (no network). A real provider must pass the
 * same: a non-blank answer, citations that resolve against the evidence it received, and failures as
 * {@link LLMUnavailableException}.
 */
class LLMProviderContractTest {

	private static final List<EvidenceFragment> EVIDENCE = List.of(
			EvidenceFragment.create(UUID.randomUUID(), 0, "PROTO-001", "A", "uno", new float[] { 1f }),
			EvidenceFragment.create(UUID.randomUUID(), 1, "PROTO-001", "B", "dos", new float[] { 1f }));

	private static LLMProvider provider(Duration latency, Duration timeout) {
		return new OllamaLLMAdapter(new OllamaProperties("http://unused:11434", "fixture-model", timeout,
				new OllamaProperties.Fixture(latency)));
	}

	@Test
	void answerIsNotBlankAndItsCitationsResolveAgainstTheEvidenceItReceived() {
		String answer = provider(Duration.ZERO, Duration.ofSeconds(5)).generate("pregunta", EVIDENCE).block();

		assertThat(answer).isNotBlank();
		GroundedAnswer parsed = new JacksonGroundedAnswerDecoder().decode(answer);
		CorpusIndex index = new CorpusIndex();
		index.refresh(parsed.fuentes());
		assertThat(CitationValidator.resolve(parsed, EVIDENCE, index)).hasSize(EVIDENCE.size());
	}

	@Test
	void failureSurfacesAsLlmUnavailable() {
		StepVerifier.create(provider(Duration.ofSeconds(1), Duration.ofMillis(50)).generate("pregunta", EVIDENCE))
				.expectError(LLMUnavailableException.class)
				.verify(Duration.ofSeconds(5));
	}

}
