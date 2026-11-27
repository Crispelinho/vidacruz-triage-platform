package com.vidacruz.clinical.application.controller;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.vidacruz.clinical.application.dto.CorpusIngestRequestDTO;
import com.vidacruz.clinical.domain.models.IngestedDocument;
import com.vidacruz.clinical.domain.models.RetrievedChunk;
import com.vidacruz.clinical.domain.services.CorpusIngestionService;
import com.vidacruz.clinical.domain.services.RagRetrievalService;

import reactor.core.publisher.Mono;

/** Standalone WebFlux test: no Spring context, no security, no database. */
class CorpusControllerTest {

	private CorpusIngestionService ingestion;

	private RagRetrievalService retrieval;

	private WebTestClient client;

	@BeforeEach
	void setUp() {
		ingestion = mock(CorpusIngestionService.class);
		retrieval = mock(RagRetrievalService.class);
		client = WebTestClient.bindToController(new CorpusController(ingestion, retrieval))
				.controllerAdvice(new CorpusExceptionHandler())
				.build();
	}

	@Test
	void ingestReturnsDocumentIdAndChunksCount() {
		UUID id = UUID.randomUUID();
		when(ingestion.ingestDocument("PROTO-001", "texto")).thenReturn(Mono.just(new IngestedDocument(id, "PROTO-001", 3)));

		client.post().uri("/clinical/corpus/ingest").contentType(MediaType.APPLICATION_JSON)
				.bodyValue(new CorpusIngestRequestDTO("PROTO-001", "texto"))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.documentId").isEqualTo(id.toString())
				.jsonPath("$.chunksCount").isEqualTo(3);
	}

	@Test
	void retrieveReturnsTextSimilarityAndSourceUsingDefaultThreshold() {
		RetrievedChunk chunk = new RetrievedChunk(UUID.randomUUID(), 0, "PA >= 180/120", 0.91, "PROTO-001");
		when(retrieval.retrieve(eq("crisis"), anyDouble())).thenReturn(Mono.just(List.of(chunk)));

		client.get().uri("/clinical/corpus/retrieve?query=crisis")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.length()").isEqualTo(1)
				.jsonPath("$[0].text").isEqualTo("PA >= 180/120")
				.jsonPath("$[0].similarity").isEqualTo(0.91)
				.jsonPath("$[0].source").isEqualTo("PROTO-001")
				.jsonPath("$[0].embedding").doesNotExist();
		verify(retrieval).retrieve("crisis", 0.75);
	}

	@Test
	void retrieveForwardsExplicitThreshold() {
		when(retrieval.retrieve("q", 0.9)).thenReturn(Mono.just(List.of()));

		client.get().uri("/clinical/corpus/retrieve?query=q&threshold=0.9")
				.exchange()
				.expectStatus().isOk()
				.expectBody().json("[]");
	}

	@Test
	void missingQueryIs400WithoutCallingTheService() {
		client.get().uri("/clinical/corpus/retrieve").exchange().expectStatus().isBadRequest();
		verifyNoInteractions(retrieval);
	}

	@Test
	void validationFailureIs400WithErrorBody() {
		when(ingestion.ingestDocument("", "x")).thenReturn(Mono.error(
				new com.vidacruz.clinical.domain.exceptions.InvalidCorpusRequestException("title must not be blank")));

		client.post().uri("/clinical/corpus/ingest").contentType(MediaType.APPLICATION_JSON)
				.bodyValue(new CorpusIngestRequestDTO("", "x"))
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.code").isEqualTo("INVALID_REQUEST")
				.jsonPath("$.message").isEqualTo("title must not be blank")
				.jsonPath("$.correlationId").isNotEmpty();
	}

}
