package com.vidacruz.clinical.application.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import com.vidacruz.clinical.application.dto.ErrorResponseDTO;
import com.vidacruz.clinical.domain.exceptions.InvalidCitationException;
import com.vidacruz.clinical.domain.exceptions.InvalidLLMResponseException;
import com.vidacruz.clinical.domain.exceptions.InvalidPromptException;
import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;
import com.vidacruz.clinical.domain.ports.ConsultationMetrics;

/** Maps RAG failures to the API-04 error bodies. Messages never include question or answer text (CA-G-16). */
@RestControllerAdvice(assignableTypes = RAGController.class)
public class RAGExceptionHandler {

	static final String CORRELATION_HEADER = "X-Correlation-Id";

	static final String LLM_UNAVAILABLE_MESSAGE = "El asistente no está disponible temporalmente";

	static final String INVALID_LLM_RESPONSE_MESSAGE = "La respuesta del asistente no pudo validarse";

	private final ConsultationMetrics metrics;

	public RAGExceptionHandler(ConsultationMetrics metrics) {
		this.metrics = metrics;
	}

	@ExceptionHandler(InvalidPromptException.class)
	ResponseEntity<ErrorResponseDTO> invalidPrompt(InvalidPromptException ex, ServerWebExchange exchange) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponseDTO("INVALID_REQUEST", ex.getMessage(), correlationId(exchange)));
	}

	@ExceptionHandler(LLMUnavailableException.class)
	ResponseEntity<ErrorResponseDTO> llmUnavailable(LLMUnavailableException ex, ServerWebExchange exchange) {
		metrics.unavailable();
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ErrorResponseDTO("LLM_UNAVAILABLE", LLM_UNAVAILABLE_MESSAGE, correlationId(exchange)));
	}

	/**
	 * RN-05.3-C / anti-hallucination: invented or out-of-context citations must not be returned.
	 * Respond with the fixed notice and no clinical content.
	 */
	@ExceptionHandler(InvalidCitationException.class)
	ResponseEntity<com.vidacruz.clinical.application.dto.RAGResponseDTO> invalidCitation(InvalidCitationException ex,
			ServerWebExchange exchange) {
		metrics.invalidCitation();
		return ResponseEntity.ok(com.vidacruz.clinical.application.dto.RAGResponseDTO
				.from(com.vidacruz.clinical.domain.models.RAGResponse.noEvidence()));
	}

	/** G-04: a JSON parse error is rejected directly with the fixed notice; no regeneration is attempted. */
	@ExceptionHandler(InvalidLLMResponseException.class)
	ResponseEntity<com.vidacruz.clinical.application.dto.RAGResponseDTO> invalidLlmResponse(
			InvalidLLMResponseException ex, ServerWebExchange exchange) {
		metrics.invalidCitation();
		return ResponseEntity.ok(com.vidacruz.clinical.application.dto.RAGResponseDTO
				.from(com.vidacruz.clinical.domain.models.RAGResponse.noEvidence()));
	}

	private static String correlationId(ServerWebExchange exchange) {
		String header = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
		return header != null && !header.isBlank() ? header : UUID.randomUUID().toString();
	}

}
