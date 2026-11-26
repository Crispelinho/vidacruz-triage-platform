package com.vidacruz.clinical.application.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import com.vidacruz.clinical.application.dto.ErrorResponseDTO;
import com.vidacruz.clinical.domain.exceptions.InvalidPromptException;
import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;

/** Maps RAG failures to the API-04 error bodies. Messages never include question or answer text (CA-G-16). */
@RestControllerAdvice(assignableTypes = RAGController.class)
public class RAGExceptionHandler {

	static final String CORRELATION_HEADER = "X-Correlation-Id";

	static final String LLM_UNAVAILABLE_MESSAGE = "El asistente no está disponible temporalmente";

	@ExceptionHandler(InvalidPromptException.class)
	ResponseEntity<ErrorResponseDTO> invalidPrompt(InvalidPromptException ex, ServerWebExchange exchange) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponseDTO("INVALID_REQUEST", ex.getMessage(), correlationId(exchange)));
	}

	@ExceptionHandler(LLMUnavailableException.class)
	ResponseEntity<ErrorResponseDTO> llmUnavailable(LLMUnavailableException ex, ServerWebExchange exchange) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ErrorResponseDTO("LLM_UNAVAILABLE", LLM_UNAVAILABLE_MESSAGE, correlationId(exchange)));
	}

	private static String correlationId(ServerWebExchange exchange) {
		String header = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
		return header != null && !header.isBlank() ? header : UUID.randomUUID().toString();
	}

}
