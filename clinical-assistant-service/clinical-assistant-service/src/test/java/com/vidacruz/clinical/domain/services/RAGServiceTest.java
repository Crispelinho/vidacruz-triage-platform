package com.vidacruz.clinical.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.PromptRequest;
import com.vidacruz.clinical.domain.models.RAGResponse;
import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.LLMRouter;
import com.vidacruz.clinical.domain.ports.LLMProvider;
import com.vidacruz.clinical.domain.ports.VectorStore;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RAGServiceTest {

	private static final float[] QUERY_EMBEDDING = { 0.1f, 0.2f, 0.3f };

	@Mock
	private EmbeddingProvider embeddingProvider;

	@Mock
	private VectorStore vectorStore;

	@Mock
	private LLMProvider llmProvider;

	private RAGService service;

	@BeforeEach
	void setUp() {
		service = new RAGService(embeddingProvider, vectorStore, llmProvider);
	}

	private static EvidenceFragment fragment(String protocol, String section) {
		return EvidenceFragment.create(UUID.randomUUID(), 0, protocol, section, "texto del protocolo",
				new float[] { 0.1f, 0.2f, 0.3f });
	}

	private static com.vidacruz.clinical.domain.models.ScoredEvidenceFragment scored(EvidenceFragment fragment,
			double score) {
		return new com.vidacruz.clinical.domain.models.ScoredEvidenceFragment(fragment, score);
	}

	@Test
	void happyPathEmbedsRetrievesThenGenerates() {
		PromptRequest request = new PromptRequest("medidas ante crisis hipertensiva", 2);
		List<EvidenceFragment> evidence = List.of(fragment("PROTO-001", "Medidas iniciales"),
				fragment("PROTO-001", "Seguimiento"));
		when(embeddingProvider.embed("medidas ante crisis hipertensiva")).thenReturn(Mono.just(QUERY_EMBEDDING));
		when(vectorStore.similaritySearchWithScores(QUERY_EMBEDDING, 2))
				.thenReturn(Mono.just(evidence.stream().map(f -> scored(f, 0.9d)).toList()));
		when(llmProvider.generate("medidas ante crisis hipertensiva", evidence)).thenReturn(Mono.just("respuesta [1]"));

		StepVerifier.create(service.answer(request)).assertNext(response -> {
			assertThat(response.answer()).isEqualTo("respuesta [1]");
			assertThat(response.sources()).isEqualTo(evidence);
			assertThat(response.hasEvidence()).isTrue();
		}).verifyComplete();

		InOrder order = inOrder(embeddingProvider, vectorStore, llmProvider);
		order.verify(embeddingProvider).embed("medidas ante crisis hipertensiva");
		order.verify(vectorStore).similaritySearchWithScores(QUERY_EMBEDDING, 2);
		order.verify(llmProvider).generate("medidas ante crisis hipertensiva", evidence);
	}

	@Test
	void noEvidenceReturnsFixedNoticeAndNeverCallsTheLlm() {
		when(embeddingProvider.embed("diabetes tipo 2")).thenReturn(Mono.just(QUERY_EMBEDDING));
		when(vectorStore.similaritySearchWithScores(QUERY_EMBEDDING, PromptRequest.DEFAULT_TOP_K))
				.thenReturn(Mono.just(List.of()));

		StepVerifier.create(service.answer(PromptRequest.of("diabetes tipo 2")))
				.expectNext(RAGResponse.noEvidence())
				.verifyComplete();

		verifyNoInteractions(llmProvider);
	}

	@Test
	void belowThresholdReturnsFixedNoticeAndNeverCallsTheLlm() {
		List<EvidenceFragment> evidence = List.of(fragment("PROTO-001", "Medidas iniciales"));
		service = new RAGService(embeddingProvider, vectorStore, (LLMRouter) (q, ev) -> llmProvider.generate(q, ev).map(a -> new com.vidacruz.clinical.domain.models.LLMCompletion("single", "unknown", a)), 0.75d);
		when(embeddingProvider.embed("q")).thenReturn(Mono.just(QUERY_EMBEDDING));
		when(vectorStore.similaritySearchWithScores(QUERY_EMBEDDING, PromptRequest.DEFAULT_TOP_K))
				.thenReturn(Mono.just(List.of(scored(evidence.get(0), 0.4d))));

		StepVerifier.create(service.answer(PromptRequest.of("q"))).expectNext(RAGResponse.noEvidence()).verifyComplete();

		verifyNoInteractions(llmProvider);
	}

	@Test
	void requestThresholdOverridesConfiguredThreshold() {
		List<EvidenceFragment> evidence = List.of(fragment("PROTO-001", "Medidas iniciales"));
		service = new RAGService(embeddingProvider, vectorStore, (LLMRouter) (q, ev) -> llmProvider.generate(q, ev).map(a -> new com.vidacruz.clinical.domain.models.LLMCompletion("single", "unknown", a)), 0.10d);
		when(embeddingProvider.embed("q")).thenReturn(Mono.just(QUERY_EMBEDDING));
		when(vectorStore.similaritySearchWithScores(QUERY_EMBEDDING, PromptRequest.DEFAULT_TOP_K))
				.thenReturn(Mono.just(List.of(scored(evidence.get(0), 0.4d))));

		StepVerifier.create(service.answer(new PromptRequest("q", PromptRequest.DEFAULT_TOP_K, null, 0.75d)))
				.expectNext(RAGResponse.noEvidence()).verifyComplete();

		verifyNoInteractions(llmProvider);
	}

	@Test
	void invalidCitationFromLlmIsRejected() {
		List<EvidenceFragment> evidence = List.of(fragment("PROTO-001", "Medidas iniciales"));
		when(embeddingProvider.embed("q")).thenReturn(Mono.just(QUERY_EMBEDDING));
		when(vectorStore.similaritySearchWithScores(QUERY_EMBEDDING, PromptRequest.DEFAULT_TOP_K))
				.thenReturn(Mono.just(List.of(scored(evidence.get(0), 0.9d))));
		when(llmProvider.generate("q", evidence)).thenReturn(Mono.just("respuesta inventada [9]"));

		StepVerifier.create(service.answer(PromptRequest.of("q")))
				.expectError(com.vidacruz.clinical.domain.exceptions.InvalidCitationException.class).verify();
	}

	@Test
	void llmFailurePropagatesAsLlmUnavailable() {
		List<EvidenceFragment> evidence = List.of(fragment("PROTO-001", "Medidas iniciales"));
		when(embeddingProvider.embed("q")).thenReturn(Mono.just(QUERY_EMBEDDING));
		when(vectorStore.similaritySearchWithScores(QUERY_EMBEDDING, PromptRequest.DEFAULT_TOP_K))
				.thenReturn(Mono.just(List.of(scored(evidence.get(0), 0.9d))));
		when(llmProvider.generate("q", evidence)).thenReturn(Mono.error(new LLMUnavailableException("timeout")));

		StepVerifier.create(service.answer(PromptRequest.of("q"))).expectError(LLMUnavailableException.class).verify();
	}

	@Test
	void retrievalFailureDoesNotReachTheLlm() {
		when(embeddingProvider.embed("q")).thenReturn(Mono.just(QUERY_EMBEDDING));
		when(vectorStore.similaritySearchWithScores(QUERY_EMBEDDING, PromptRequest.DEFAULT_TOP_K))
				.thenReturn(Mono.error(new IllegalStateException("db down")));

		StepVerifier.create(service.answer(PromptRequest.of("q"))).expectError(IllegalStateException.class).verify();

		verify(vectorStore).similaritySearchWithScores(QUERY_EMBEDDING, PromptRequest.DEFAULT_TOP_K);
		verifyNoInteractions(llmProvider);
	}

	@Test
	void constructorRejectsNullCollaborators() {
		assertThatThrownBy(() -> new RAGService(null, vectorStore, llmProvider))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new RAGService(embeddingProvider, null, llmProvider))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new RAGService(embeddingProvider, vectorStore, (LLMRouter) null))
				.isInstanceOf(NullPointerException.class);
	}

}
