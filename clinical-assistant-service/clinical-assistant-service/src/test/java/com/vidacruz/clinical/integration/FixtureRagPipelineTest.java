package com.vidacruz.clinical.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.exceptions.InvalidCitationException;
import com.vidacruz.clinical.domain.models.Citation;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.PromptRequest;
import com.vidacruz.clinical.domain.models.RAGResponse;
import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.LLMProvider;
import com.vidacruz.clinical.domain.services.RAGService;
import com.vidacruz.clinical.infrastructure.adapters.FixtureEmbeddingAdapter;
import com.vidacruz.clinical.infrastructure.adapters.FixtureVectorStoreAdapter;
import com.vidacruz.clinical.infrastructure.adapters.OllamaLLMAdapter;
import com.vidacruz.clinical.infrastructure.config.OllamaProperties;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fixture-to-fixture pipeline: fixture embeddings, in-memory store, fixture LLM. It proves the wiring and the
 * citation/provenance rules. It does NOT prove retrieval quality: fixture embeddings carry no semantics, so a
 * fragment is found only because its embedding was produced from the same text as the query.
 *
 * <p>All corpus text is synthetic test data, not clinical content.
 */
class FixtureRagPipelineTest {

	private static final int DIMENSIONS = 768;

	private final EmbeddingProvider embeddings = new FixtureEmbeddingAdapter(DIMENSIONS);

	private FixtureVectorStoreAdapter store;

	private RAGService service;

	@BeforeEach
	void setUp() {
		store = new FixtureVectorStoreAdapter();
		LLMProvider llm = new OllamaLLMAdapter(new OllamaProperties("http://unused:11434", "fixture-model",
				Duration.ofSeconds(5), new OllamaProperties.Fixture(Duration.ZERO)));
		service = new RAGService(embeddings, store, llm);
	}

	/** Ingests a synthetic fragment whose embedding is derived from {@code topic}. */
	private EvidenceFragment ingest(String topic, String protocol, String section) {
		float[] embedding = embeddings.embed(topic).block();
		EvidenceFragment fragment = EvidenceFragment.create(UUID.randomUUID(), 0, protocol, section,
				"[FIXTURE] contenido sintetico sobre " + topic, embedding);
		return store.save(fragment).block();
	}

	@Test
	void retrievesTheMatchingFragmentFirstAndCitesIt() {
		EvidenceFragment match = ingest("tema-alfa", "FIXTURE-A", "Seccion A");
		ingest("tema-beta", "FIXTURE-B", "Seccion B");
		ingest("tema-gamma", "FIXTURE-C", "Seccion C");

		StepVerifier.create(service.answer(new PromptRequest("tema-alfa", 2))).assertNext(response -> {
			assertThat(response.sources()).hasSize(2);
			assertThat(response.sources().get(0).id()).isEqualTo(match.id());
			assertThat(response.citations().get(0)).isEqualTo(Citation.of(1, match));
			assertThat(response.answer()).contains("[1]").contains("[2]").doesNotContain("[3]");
		}).verifyComplete();
	}

	@Test
	void everyCitationPointsToAFragmentThatExistsInTheStore() {
		for (String topic : List.of("t1", "t2", "t3", "t4")) {
			ingest(topic, "FIXTURE-" + topic, "Seccion");
		}

		RAGResponse response = service.answer(new PromptRequest("t1", 4)).block();

		assertThat(response.citations()).hasSize(4);
		for (Citation citation : response.citations()) {
			EvidenceFragment stored = store.findById(citation.fragmentId()).block();
			assertThat(stored).isNotNull();
			assertThat(stored.protocol()).isEqualTo(citation.protocol());
			assertThat(stored.section()).isEqualTo(citation.section());
			assertThat(stored.documentId()).isEqualTo(citation.documentId());
		}
	}

	@Test
	void emptyCorpusAnswersWithTheFixedNoticeAndNoSources() {
		StepVerifier.create(service.answer(PromptRequest.of("cualquier cosa")))
				.expectNext(RAGResponse.noEvidence())
				.verifyComplete();
	}

	@Test
	void topKBoundsTheEvidenceUsed() {
		for (int i = 0; i < 10; i++) {
			ingest("tema-" + i, "FIXTURE-" + i, "Seccion");
		}

		RAGResponse response = service.answer(new PromptRequest("tema-3", 3)).block();

		assertThat(response.sources()).hasSize(3);
	}

	@Test
	void anAnswerCitingEvidenceThatWasNotRetrievedIsRejected() {
		ingest("tema-alfa", "FIXTURE-A", "Seccion A");
		LLMProvider hallucinating = (question, evidence) -> Mono.just("segun el protocolo [7]");
		RAGService strict = new RAGService(embeddings, store, hallucinating);

		StepVerifier.create(strict.answer(PromptRequest.of("tema-alfa"))).expectError(InvalidCitationException.class)
				.verify();
	}

	@Test
	void fixtureRetrievalLatencyStaysFarBelowTheInteractiveBudget() {
		for (int i = 0; i < 200; i++) {
			ingest("tema-" + i, "FIXTURE-" + i, "Seccion");
		}
		service.answer(new PromptRequest("tema-1", 4)).block(); // warm up

		long start = System.nanoTime();
		for (int i = 0; i < 20; i++) {
			service.answer(new PromptRequest("tema-" + i, 4)).block();
		}
		long averageMillis = Duration.ofNanos((System.nanoTime() - start) / 20).toMillis();

		// Generous bound: it only guards against accidental quadratic work, it is not a performance claim.
		assertThat(averageMillis).isLessThan(500);
	}

}
