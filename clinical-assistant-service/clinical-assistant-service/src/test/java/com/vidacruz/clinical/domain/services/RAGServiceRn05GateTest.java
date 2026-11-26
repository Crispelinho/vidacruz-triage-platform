package com.vidacruz.clinical.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.PromptRequest;
import com.vidacruz.clinical.domain.models.ScoredEvidenceFragment;
import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.LLMRouter;
import com.vidacruz.clinical.domain.ports.VectorStore;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RAGServiceRn05GateTest {

	@Test
	void scenarioB_belowThresholdDoesNotCallLlm() {
		EmbeddingProvider embedding = mock(EmbeddingProvider.class);
		VectorStore store = mock(VectorStore.class);
		LLMRouter llm = mock(LLMRouter.class);
		EvidenceFragment fragment = EvidenceFragment.create(UUID.randomUUID(), 0, "PROTO-001", "s", "content",
				new float[] { 1f });
		when(embedding.embed("q")).thenReturn(Mono.just(new float[] { 1f }));
		when(store.similaritySearchWithScores(new float[] { 1f }, 4))
				.thenReturn(Mono.just(List.of(new ScoredEvidenceFragment(fragment, 0.2d))));

		RAGService service = new RAGService(embedding, store, llm, 0.75d);

		StepVerifier.create(service.answer(PromptRequest.of("q")))
				.assertNext(response -> {
					assertThat(response.hasEvidence()).isFalse();
					assertThat(response.answer()).isEqualTo("no existe protocolo disponible");
				})
				.verifyComplete();
	}
}
