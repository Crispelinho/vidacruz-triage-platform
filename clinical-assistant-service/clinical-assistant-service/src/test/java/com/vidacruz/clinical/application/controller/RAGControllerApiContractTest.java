package com.vidacruz.clinical.application.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.vidacruz.clinical.domain.exceptions.InvalidCitationException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.PromptRequest;
import com.vidacruz.clinical.domain.models.RAGResponse;
import com.vidacruz.clinical.domain.services.RAGService;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/** HTTP contract of POST /rag beyond the base cases: citations, optional fields, and the upstream-failure mapping. */
class RAGControllerApiContractTest {

	private RAGService ragService;

	private WebTestClient client;

	@BeforeEach
	void setUp() {
		ragService = mock(RAGService.class);
		client = WebTestClient.bindToController(new RAGController(ragService))
				.controllerAdvice(new RAGExceptionHandler())
				.build();
	}

	@Test
	void responseCarriesCitationsNumberedLikeTheAnswerMarkersAndNoConfidenceField() {
		EvidenceFragment first = EvidenceFragment.create(UUID.randomUUID(), 2, "PROTO-001", "Medidas",
				"texto reservado", new float[] { 1f });
		EvidenceFragment second = EvidenceFragment.create(UUID.randomUUID(), 0, "PROTO-002", "Seguimiento",
				"otro texto", new float[] { 1f });
		when(ragService.answer(any(PromptRequest.class)))
				.thenReturn(Mono.just(new RAGResponse("segun [1] y [2]", List.of(first, second))));

		client.post().uri("/rag").contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"pregunta\":\"q\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.citas.length()").isEqualTo(2)
				.jsonPath("$.citas[0].posicion").isEqualTo(1)
				.jsonPath("$.citas[0].fragmentoId").isEqualTo(first.id().toString())
				.jsonPath("$.citas[0].documentoId").isEqualTo(first.documentId().toString())
				.jsonPath("$.citas[0].indiceFragmento").isEqualTo(2)
				.jsonPath("$.citas[0].protocolo").isEqualTo("PROTO-001")
				.jsonPath("$.citas[1].posicion").isEqualTo(2)
				.jsonPath("$.citas[1].seccion").isEqualTo("Seguimiento")
				.jsonPath("$.confianza").doesNotExist()
				.jsonPath("$..embedding").doesNotExist()
				.jsonPath("$..content").doesNotExist()
				.jsonPath("$..contenido").doesNotExist();
	}

	@Test
	void noEvidenceHasNoCitations() {
		when(ragService.answer(any(PromptRequest.class))).thenReturn(Mono.just(RAGResponse.noEvidence()));

		client.post().uri("/rag").contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"pregunta\":\"q\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.citas.length()").isEqualTo(0);
	}

	@Test
	void optionalFieldsReachTheDomainRequestUnderTheirSnakeCaseNames() {
		when(ragService.answer(any(PromptRequest.class))).thenReturn(Mono.just(RAGResponse.noEvidence()));

		client.post().uri("/rag").contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"pregunta\":\"q\",\"contexto_paciente\":\"  antecedentes  \",\"umbral_relevancia\":0.4}")
				.exchange()
				.expectStatus().isOk();

		ArgumentCaptor<PromptRequest> captor = ArgumentCaptor.forClass(PromptRequest.class);
		verify(ragService).answer(captor.capture());
		assertThat(captor.getValue().patientContext()).isEqualTo("antecedentes");
		assertThat(captor.getValue().relevanceThreshold()).isEqualTo(0.4);
	}

	@Test
	void thresholdOutOfRangeIsA400AndNeverReachesTheService() {
		client.post().uri("/rag").contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"pregunta\":\"q\",\"umbral_relevancia\":1.5}")
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.code").isEqualTo("INVALID_REQUEST");

		verifyNoInteractions(ragService);
	}

	@Test
	void oversizedQuestionIsA400() {
		String body = "{\"pregunta\":\"" + "x".repeat(PromptRequest.MAX_QUESTION_LENGTH + 1) + "\"}";

		client.post().uri("/rag").contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.code").isEqualTo("INVALID_REQUEST");

		verifyNoInteractions(ragService);
	}

	@Test
	void invalidCitationIsA502WithoutLeakingAnswerText() {
		when(ragService.answer(any(PromptRequest.class)))
				.thenReturn(Mono.error(new InvalidCitationException("answer cites position 7 but only 1 exist")));

		client.post().uri("/rag").contentType(MediaType.APPLICATION_JSON)
				.header("X-Correlation-Id", "corr-9")
				.bodyValue("{\"pregunta\":\"q\"}")
				.exchange()
				.expectStatus().isEqualTo(502)
				.expectBody()
				.jsonPath("$.code").isEqualTo("INVALID_LLM_RESPONSE")
				.jsonPath("$.message").isEqualTo("La respuesta del asistente no pudo validarse")
				.jsonPath("$.correlationId").isEqualTo("corr-9");
	}

}
