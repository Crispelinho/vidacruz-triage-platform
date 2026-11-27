package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.infrastructure.config.OllamaProperties;

import reactor.test.StepVerifier;

/** Uses virtual time: the realistic latencies cost nothing and no network is touched. */
class OllamaLLMAdapterTest {

	private static final List<EvidenceFragment> EVIDENCE = List.of(
			EvidenceFragment.create(UUID.randomUUID(), 0, "PROTO-001", "Medidas iniciales", "texto reservado",
					new float[] { 1f }),
			EvidenceFragment.create(UUID.randomUUID(), 1, "PROTO-001", "Seguimiento", "otro texto",
					new float[] { 1f }));

	private static OllamaLLMAdapter adapter(Duration latency, Duration timeout) {
		return new OllamaLLMAdapter(new OllamaProperties("http://localhost:11434", "llama3.1:8b", timeout,
				new OllamaProperties.Fixture(latency)));
	}

	@Test
	void answersWithTheFixtureOnlyAfterTheSimulatedLatency() {
		StepVerifier.withVirtualTime(() -> adapter(Duration.ofMillis(1200), Duration.ofSeconds(30))
				.generate("pregunta", EVIDENCE))
				.expectSubscription()
				.expectNoEvent(Duration.ofMillis(1199))
				.thenAwait(Duration.ofMillis(1))
				.assertNext(answer -> assertThat(answer).contains("\"respuesta\":\"[FIXTURE:llama3.1:8b]")
						.contains("2 fragmento(s)").contains("\"fuentes\""))
				.verifyComplete();
	}

	@Test
	void doesNotEchoQuestionOrEvidenceText() {
		StepVerifier.withVirtualTime(() -> adapter(Duration.ofMillis(10), Duration.ofSeconds(30))
				.generate("dolor toracico del paciente X", EVIDENCE))
				.thenAwait(Duration.ofMillis(10))
				.assertNext(answer -> assertThat(answer).doesNotContain("dolor").doesNotContain("texto reservado"))
				.verifyComplete();
	}

	@Test
	void timeoutBecomesLlmUnavailable() {
		StepVerifier.withVirtualTime(() -> adapter(Duration.ofSeconds(10), Duration.ofSeconds(2))
				.generate("pregunta", EVIDENCE))
				.expectSubscription()
				.expectNoEvent(Duration.ofMillis(1999))
				.thenAwait(Duration.ofMillis(1))
				.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(LLMUnavailableException.class)
						.hasCauseInstanceOf(TimeoutException.class)
						.hasMessageNotContaining("pregunta"))
				.verify();
	}

	@Test
	void latencyJustBelowTheTimeoutStillSucceeds() {
		StepVerifier.withVirtualTime(() -> adapter(Duration.ofMillis(1999), Duration.ofSeconds(2))
				.generate("pregunta", EVIDENCE))
				.thenAwait(Duration.ofMillis(1999))
				.expectNextCount(1)
				.verifyComplete();
	}

	@Test
	void propertiesRejectNonPositiveTimeoutAndNegativeLatency() {
		org.assertj.core.api.Assertions
				.assertThatThrownBy(() -> new OllamaProperties("u", "m", Duration.ZERO,
						new OllamaProperties.Fixture(Duration.ZERO)))
				.isInstanceOf(IllegalArgumentException.class);
		org.assertj.core.api.Assertions
				.assertThatThrownBy(() -> new OllamaProperties.Fixture(Duration.ofMillis(-1)))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
