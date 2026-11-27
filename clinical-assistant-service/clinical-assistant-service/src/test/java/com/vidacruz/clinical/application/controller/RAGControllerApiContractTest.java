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
import com.vidacruz.clinical.infrastructure.metrics.MicrometerConsultationMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/** HTTP contract of POST /rag beyond the base cases: citations, optional fields, and the upstream-failure mapping. */
class RAGControllerApiContractTest {

	private RAGService ragService;

	private WebTestClient client;

	private SimpleMeterRegistry registry;

	@BeforeEach
	void setUp() {
		ragService = mock(RAGService.class);
		registry = new SimpleMeterRegistry();
		MicrometerConsultationMetrics metrics = new MicrometerConsultationMetrics(registry);
		client = WebTestClient.bindToController(new RAGController(ragService, metrics))
				.controllerAdvice(new RAGExceptionHandler(metrics))
				.build();
	}

	@Test
	void responseCarriesCitationsNumberedLikeTheAnswerMarkersAndNoConfidenceField() {
		EvidenceFragment first = EvidenceFragment.create(UUID.randomUUID(), 2, "PROTO-001", "Medidas",
				"texto reservado", new float[] { 1f });
		EvidenceFragment second = EvidenceFragment.create(UUID.randomUUID(), 0, "PROTO-002", "Seguimiento",
				"otro texto", new float[] { 1f });
		when(ragService.answer(any(PromptRequest.class)))
				.thenReturn(Mono.just(new RAGResponse("segun [1] y [2]", List.of(first, second),
						"ollama/llama3.1:8b")));

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
				.jsonPath("$.modelo_usado").isEqualTo("ollama/llama3.1:8b")
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
				.jsonPath("$.citas.length()").isEqualTo(0)
				.jsonPath("$.modelo_usado").isEqualTo("none");
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
	void invalidCitationReturnsFixedNoticeWithoutLeakingAnswerText() {
		when(ragService.answer(any(PromptRequest.class)))
				.thenReturn(Mono.error(new InvalidCitationException("answer cites position 7 but only 1 exist")));

		client.post().uri("/rag").contentType(MediaType.APPLICATION_JSON)
				.header("X-Correlation-Id", "corr-9")
				.bodyValue("{\"pregunta\":\"q\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.respuesta").isEqualTo("no existe protocolo disponible")
				.jsonPath("$.fuentes.length()").isEqualTo(0)
				.jsonPath("$.citas.length()").isEqualTo(0)
				.jsonPath("$.modelo_usado").isEqualTo("none");

		assertThat(registry.get("assistant.answers").tag("outcome", "invalid_citation").counter().count())
				.isEqualTo(1.0d);
	}

}

