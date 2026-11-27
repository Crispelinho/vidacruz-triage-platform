package com.vidacruz.clinical.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
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

import com.vidacruz.clinical.domain.exceptions.InvalidCitationException;
import com.vidacruz.clinical.domain.exceptions.InvalidLLMResponseException;
import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.PromptRequest;
import com.vidacruz.clinical.domain.models.RAGResponse;
import com.vidacruz.clinical.domain.models.ScoredEvidenceFragment;
import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
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

	private CorpusIndex corpusIndex;

	private RAGService service;

	@BeforeEach
	void setUp() {
		corpusIndex = new CorpusIndex();
		service = new RAGService(embeddingProvider, vectorStore, llmProvider, corpusIndex);
	}

	private static EvidenceFragment fragment(String protocol, String section) {
		return EvidenceFragment.create(UUID.randomUUID(), 0, protocol, section, "texto del protocolo",
				new float[] { 0.1f, 0.2f, 0.3f });
	}

	private static ScoredEvidenceFragment scored(EvidenceFragment fragment, double score) {
		return new ScoredEvidenceFragment(fragment, score);
	}

	private static String answer(List<EvidenceFragment> evidence, String text) {
		StringBuilder json = new StringBuilder("{\"respuesta\":\"").append(text)
				.append("\",\"fuentes\":[");
		for (int i = 0; i < evidence.size(); i++) {
			if (i > 0) {
				json.append(',');
			}
			EvidenceFragment fragment = evidence.get(i);
			json.append("{\"protocolo\":\"").append(fragment.protocol()).append("\",\"seccion\":\"")
					.append(fragment.section()).append("\"}");
		}
		return json.append("]}").toString();
	}

	private RAGService withThreshold(double threshold) {
		com.vidacruz.clinical.domain.ports.LLMRouter router = (q, evidence) -> llmProvider.generate(q, evidence)
				.map(answer -> new com.vidacruz.clinical.domain.models.LLMCompletion("single", "unknown", answer));
		return new RAGService(embeddingProvider, vectorStore, router, corpusIndex, threshold);
	}

	@Test
	void happyPathEmbedsRetrievesParsesJsonThenValidatesCitations() {
		PromptRequest request = new PromptRequest("medidas ante crisis hipertensiva", 2);
		List<EvidenceFragment> evidence = List.of(fragment("PROTO-001", "Medidas iniciales"),
				fragment("PROTO-001", "Seguimiento"));
		corpusIndex.refresh(evidence.stream().map(com.vidacruz.clinical.domain.models.ProtocolSection::from).toList());
		when(embeddingProvider.embed("medidas ante crisis hipertensiva")).thenReturn(Mono.just(QUERY_EMBEDDING));
		when(vectorStore.similaritySearchWithScores(QUERY_EMBEDDING, 2))
				.thenReturn(Mono.just(evidence.stream().map(f -> scored(f, 0.9d)).toList()));
		when(llmProvider.generate("medidas ante crisis hipertensiva", evidence))
				.thenReturn(Mono.just(answer(evidence, "respuesta [1] y [2]")));

		StepVerifier.create(service.answer(request)).assertNext(response -> {
			assertThat(response.answer()).isEqualTo("respuesta [1] y [2]");
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
		RAGService strict = withThreshold(0.75d);
		when(embeddingProvider.embed("q")).thenReturn(Mono.just(QUERY_EMBEDDING));
		when(vectorStore.similaritySearchWithScores(QUERY_EMBEDDING, PromptRequest.DEFAULT_TOP_K))
				.thenReturn(Mono.just(List.of(scored(evidence.get(0), 0.4d))));

		StepVerifier.create(strict.answer(PromptRequest.of("q"))).expectNext(RAGResponse.noEvidence()).verifyComplete();

		verifyNoInteractions(llmProvider);
	}

	@Test
	void requestThresholdOverridesConfiguredThreshold() {
		List<EvidenceFragment> evidence = List.of(fragment("PROTO-001", "Medidas iniciales"));
		RAGService lenient = withThreshold(0.10d);
		when(embeddingProvider.embed("q")).thenReturn(Mono.just(QUERY_EMBEDDING));
		when(vectorStore.similaritySearchWithScores(QUERY_EMBEDDING, PromptRequest.DEFAULT_TOP_K))
				.thenReturn(Mono.just(List.of(scored(evidence.get(0), 0.4d))));

		StepVerifier.create(lenient.answer(new PromptRequest("q", PromptRequest.DEFAULT_TOP_K, null, 0.75d)))
				.expectNext(RAGResponse.noEvidence()).verifyComplete();

		verifyNoInteractions(llmProvider);
	}

	@Test
	void malformedJsonIsRejectedDirectlyWithoutRegeneration() {
		List<EvidenceFragment> evidence = List.of(fragment("PROTO-001", "Medidas iniciales"));
		when(embeddingProvider.embed("q")).thenReturn(Mono.just(QUERY_EMBEDDING));
		when(vectorStore.similaritySearchWithScores(QUERY_EMBEDDING, PromptRequest.DEFAULT_TOP_K))
				.thenReturn(Mono.just(List.of(scored(evidence.get(0), 0.9d))));
		when(llmProvider.generate("q", evidence)).thenReturn(Mono.just("respuesta sin formato"));

		StepVerifier.create(service.answer(PromptRequest.of("q")))
				.expectError(InvalidLLMResponseException.class).verify();

		verify(llmProvider, times(1)).generate("q", evidence);
	}

	@Test
	void sourceOutsideTheCorpusIndexIsRejected() {
		List<EvidenceFragment> evidence = List.of(fragment("PROTO-001", "Medidas iniciales"));
		when(embeddingProvider.embed("q")).thenReturn(Mono.just(QUERY_EMBEDDING));
		when(vectorStore.similaritySearchWithScores(QUERY_EMBEDDING, PromptRequest.DEFAULT_TOP_K))
				.thenReturn(Mono.just(List.of(scored(evidence.get(0), 0.9d))));
		when(llmProvider.generate("q", evidence)).thenReturn(Mono.just(answer(evidence, "respuesta [1]")));

		StepVerifier.create(service.answer(PromptRequest.of("q")))
				.expectError(InvalidCitationException.class).verify();
	}

	@Test
	void sourceInsideIndexButOutsideRetrievedSetIsRejected() {
		List<EvidenceFragment> evidence = List.of(fragment("PROTO-001", "Medidas iniciales"));
		EvidenceFragment notRetrieved = fragment("PROTO-002", "Seguimiento");
		corpusIndex.add(com.vidacruz.clinical.domain.models.ProtocolSection.from(notRetrieved));
		when(embeddingProvider.embed("q")).thenReturn(Mono.just(QUERY_EMBEDDING));
		when(vectorStore.similaritySearchWithScores(QUERY_EMBEDDING, PromptRequest.DEFAULT_TOP_K))
				.thenReturn(Mono.just(List.of(scored(evidence.get(0), 0.9d))));
		when(llmProvider.generate("q", evidence)).thenReturn(Mono.just(answer(List.of(notRetrieved), "respuesta")));

		StepVerifier.create(service.answer(PromptRequest.of("q")))
				.expectError(InvalidCitationException.class).verify();
	}

	@Test
	void jsonAnswerWithoutPositionalMarkersIsAccepted() {
		List<EvidenceFragment> evidence = List.of(fragment("PROTO-001", "Medidas iniciales"));
		corpusIndex.refresh(List.of(com.vidacruz.clinical.domain.models.ProtocolSection.from(evidence.get(0))));
		when(embeddingProvider.embed("q")).thenReturn(Mono.just(QUERY_EMBEDDING));
		when(vectorStore.similaritySearchWithScores(QUERY_EMBEDDING, PromptRequest.DEFAULT_TOP_K))
				.thenReturn(Mono.just(List.of(scored(evidence.get(0), 0.9d))));
		when(llmProvider.generate("q", evidence)).thenReturn(Mono.just(answer(evidence, "respuesta sin marcadores")));

		StepVerifier.create(service.answer(PromptRequest.of("q")))
				.assertNext(response -> assertThat(response.sources()).containsExactly(evidence.get(0)))
				.verifyComplete();
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
	void constructorRejectsNullCollaboratorsAndInvalidThreshold() {
		assertThatThrownBy(() -> new RAGService(null, vectorStore, llmProvider))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new RAGService(embeddingProvider, null, llmProvider))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new RAGService(embeddingProvider, vectorStore, (com.vidacruz.clinical.domain.ports.LLMRouter) null))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> withThreshold(1.2d)).isInstanceOf(IllegalArgumentException.class);
		assertThat(RAGService.DEFAULT_RELEVANCE_THRESHOLD).isEqualTo(0.65d);
	}

}
