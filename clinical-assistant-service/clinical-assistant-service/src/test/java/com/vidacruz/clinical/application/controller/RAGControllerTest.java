package com.vidacruz.clinical.application.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.vidacruz.clinical.application.dto.RAGRequestDTO;
import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.PromptRequest;
import com.vidacruz.clinical.domain.models.RAGResponse;
import com.vidacruz.clinical.domain.services.RAGService;
import com.vidacruz.clinical.infrastructure.metrics.MicrometerConsultationMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;

/** Standalone WebFlux test: no Spring context, no security, no database. */
class RAGControllerTest {

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

	private static EvidenceFragment fragment(String protocol, String section) {
		return EvidenceFragment.create(UUID.randomUUID(), 0, protocol, section, "texto", new float[] { 1f });
	}

	@Test
	void returnsAnswerAndDeduplicatedSources() {
		RAGResponse response = new RAGResponse("respuesta",
				List.of(fragment("PROTO-001", "Medidas iniciales"), fragment("PROTO-001", "Medidas iniciales"),
						fragment("PROTO-002", "Seguimiento")));
		when(ragService.answer(any(PromptRequest.class))).thenReturn(Mono.just(response));

		client.post().uri("/rag").contentType(MediaType.APPLICATION_JSON)
				.bodyValue(new RAGRequestDTO("medidas ante crisis hipertensiva"))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.respuesta").isEqualTo("respuesta")
				.jsonPath("$.fuentes.length()").isEqualTo(2)
				.jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-001")
				.jsonPath("$.fuentes[0].seccion").isEqualTo("Medidas iniciales")
				.jsonPath("$.fuentes[1].protocolo").isEqualTo("PROTO-002")
				.jsonPath("$..embedding").doesNotExist()
				.jsonPath("$..content").doesNotExist();

		assertThat(answers("answered")).isEqualTo(1.0d);
	}

	@Test
	void noEvidenceReturns200WithFixedNoticeAndEmptySources() {
		when(ragService.answer(any(PromptRequest.class))).thenReturn(Mono.just(RAGResponse.noEvidence()));

		client.post().uri("/rag").contentType(MediaType.APPLICATION_JSON)
				.bodyValue(new RAGRequestDTO("diabetes tipo 2"))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.respuesta").isEqualTo("no existe protocolo disponible")
				.jsonPath("$.fuentes.length()").isEqualTo(0);

		assertThat(answers("no_evidence")).isEqualTo(1.0d);
	}

	@Test
	void blankQuestionIsA400WithCorrelationIdAndNoServiceCall() {
		client.post().uri("/rag").contentType(MediaType.APPLICATION_JSON)
				.header("X-Correlation-Id", "corr-123")
				.bodyValue(new RAGRequestDTO("   "))
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.code").isEqualTo("INVALID_REQUEST")
				.jsonPath("$.message").isNotEmpty()
				.jsonPath("$.correlationId").isEqualTo("corr-123");

		verifyNoInteractions(ragService);
		assertThat(registry.get("assistant.answers").meters())
				.allMatch(meter -> meter instanceof io.micrometer.core.instrument.Counter counter
						&& counter.count() == 0.0d);
	}

	@Test
	void missingQuestionFieldIsA400() {
		client.post().uri("/rag").contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.code").isEqualTo("INVALID_REQUEST")
				.jsonPath("$.correlationId").isNotEmpty();
	}

	@Test
	void llmUnavailableIsA503WithApi04Body() {
		when(ragService.answer(any(PromptRequest.class)))
				.thenReturn(Mono.error(new LLMUnavailableException("LLM call exceeded PT30S")));

		client.post().uri("/rag").contentType(MediaType.APPLICATION_JSON)
				.bodyValue(new RAGRequestDTO("pregunta"))
				.exchange()
				.expectStatus().isEqualTo(503)
				.expectBody()
				.jsonPath("$.code").isEqualTo("LLM_UNAVAILABLE")
				.jsonPath("$.message").isEqualTo("El asistente no está disponible temporalmente")
				.jsonPath("$.correlationId").isNotEmpty();

		assertThat(answers("unavailable")).isEqualTo(1.0d);
	}

	private double answers(String outcome) {
		return registry.get("assistant.answers").tag("outcome", outcome).counter().count();
	}

}
