package com.vidacruz.clinical.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.exceptions.InvalidCorpusRequestException;
import com.vidacruz.clinical.domain.models.RetrievedChunk;
import com.vidacruz.clinical.infrastructure.adapters.FixtureEmbeddingAdapter;
import com.vidacruz.clinical.infrastructure.adapters.FixtureVectorStoreAdapter;
import com.vidacruz.clinical.infrastructure.adapters.InMemoryClinicalDocumentAdapter;

import reactor.test.StepVerifier;

/**
 * Ingestion and retrieval wired over the in-memory adapters. The fixture embedding is deterministic and carries no
 * semantics: identical text gives similarity 1.0, unrelated text gives about 0. That is enough to prove the gate.
 */
class CorpusRn05Test {

	private static final int DIMENSIONS = 16;

	private FixtureVectorStoreAdapter store;

	private InMemoryClinicalDocumentAdapter documents;

	private CorpusIngestionService ingestion;

	private RagRetrievalService retrieval;

	@BeforeEach
	void setUp() {
		FixtureEmbeddingAdapter embeddings = new FixtureEmbeddingAdapter(DIMENSIONS);
		store = new FixtureVectorStoreAdapter();
		documents = new InMemoryClinicalDocumentAdapter();
		ingestion = new CorpusIngestionService(new TextChunker(4, 0.5), embeddings, store, documents);
		retrieval = new RagRetrievalService(embeddings, store, documents);
	}

	private static String words(int count) {
		return IntStream.range(0, count).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
	}

	@Test
	void ingestReturnsDocumentIdAndChunkCount() {
		// 10 words, window 4, step 2: starts 0, 2, 4, 6 -> 4 chunks.
		StepVerifier.create(ingestion.ingestDocument("PROTO-X", words(10)))
				.assertNext(doc -> {
					assertThat(doc.documentId()).isNotNull();
					assertThat(doc.chunksCount()).isEqualTo(4);
					assertThat(doc.title()).isEqualTo("PROTO-X");
				})
				.verifyComplete();
	}

	@Test
	void sameTitleAndContentGiveTheSameDocumentAndNoDuplicateChunks() {
		var first = ingestion.ingestDocument("PROTO-X", words(10)).block();
		var second = ingestion.ingestDocument("PROTO-X", words(10)).block();

		assertThat(second).isEqualTo(first);
		StepVerifier.create(retrieval.retrieve("w0 w1 w2 w3", 0.99)).assertNext(chunks -> assertThat(chunks).hasSize(1))
				.verifyComplete();
	}

	@Test
	void differentContentGivesADifferentDocumentId() {
		var a = ingestion.ingestDocument("PROTO-X", words(10)).block();
		var b = ingestion.ingestDocument("PROTO-X", words(11)).block();

		assertThat(a.documentId()).isNotEqualTo(b.documentId());
	}

	@Test
	void retrievalReturnsChunkAtOrAboveThresholdWithTitleAsSource() {
		ingestion.ingestDocument("Crisis hipertensiva", words(10)).block();

		StepVerifier.create(retrieval.retrieve("w2 w3 w4 w5", 0.75))
				.assertNext(chunks -> {
					assertThat(chunks).hasSize(1);
					RetrievedChunk chunk = chunks.get(0);
					assertThat(chunk.text()).isEqualTo("w2 w3 w4 w5");
					assertThat(chunk.similarity()).isGreaterThanOrEqualTo(0.75);
					assertThat(chunk.source()).isEqualTo("Crisis hipertensiva");
				})
				.verifyComplete();
	}

	@Test
	void rn05GateReturnsEmptyListWhenNothingReachesTheThreshold() {
		ingestion.ingestDocument("Crisis hipertensiva", words(10)).block();

		StepVerifier.create(retrieval.retrieve("tema totalmente distinto", 0.75))
				.assertNext(chunks -> assertThat(chunks).isEmpty())
				.verifyComplete();
	}

	@Test
	void rn05GateOnAnEmptyCorpusReturnsEmptyList() {
		StepVerifier.create(retrieval.retrieve("cualquier cosa"))
				.assertNext(chunks -> assertThat(chunks).isEmpty())
				.verifyComplete();
	}

	@Test
	void thresholdIsInclusive() {
		ingestion.ingestDocument("T", words(4)).block();

		// identical text: similarity is 1.0 (or within float rounding of it); a threshold of exactly the score passes.
		double score = retrieval.retrieve("w0 w1 w2 w3", 0.0).block().get(0).similarity();
		StepVerifier.create(retrieval.retrieve("w0 w1 w2 w3", score))
				.assertNext(chunks -> assertThat(chunks).hasSize(1))
				.verifyComplete();
	}

	@Test
	void returnsAtMostFiveChunks() {
		// 40 words, window 4, step 2: 19 chunks. Threshold 0 lets every chunk through the gate.
		ingestion.ingestDocument("Largo", words(40)).block();

		StepVerifier.create(retrieval.retrieve("w0 w1 w2 w3", 0.0))
				.assertNext(chunks -> assertThat(chunks).hasSize(RagRetrievalService.TOP_K))
				.verifyComplete();
	}

	@Test
	void invalidInputIsRejectedBeforeAnyWork() {
		StepVerifier.create(ingestion.ingestDocument(" ", "x")).expectError(InvalidCorpusRequestException.class)
				.verify();
		StepVerifier.create(ingestion.ingestDocument("t", " ")).expectError(InvalidCorpusRequestException.class)
				.verify();
		StepVerifier.create(retrieval.retrieve(" ", 0.5)).expectError(InvalidCorpusRequestException.class).verify();
		StepVerifier.create(retrieval.retrieve("q", 1.5)).expectError(InvalidCorpusRequestException.class).verify();
		StepVerifier.create(retrieval.retrieve("q", Double.NaN)).expectError(InvalidCorpusRequestException.class)
				.verify();
	}

	@Test
	void defaultThresholdIsPoint75() {
		assertThat(RagRetrievalService.DEFAULT_THRESHOLD).isEqualTo(0.75);
	}

}
