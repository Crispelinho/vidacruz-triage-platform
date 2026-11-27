package com.vidacruz.clinical.application.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import com.vidacruz.clinical.application.dto.ErrorResponseDTO;
import com.vidacruz.clinical.domain.exceptions.InvalidCorpusRequestException;

/** Maps corpus validation failures to the standard error body. Messages never include document or query text. */
@RestControllerAdvice(assignableTypes = CorpusController.class)
public class CorpusExceptionHandler {

	@ExceptionHandler(InvalidCorpusRequestException.class)
	ResponseEntity<ErrorResponseDTO> invalidRequest(InvalidCorpusRequestException ex, ServerWebExchange exchange) {
		String header = exchange.getRequest().getHeaders().getFirst(RAGExceptionHandler.CORRELATION_HEADER);
		String correlationId = header != null && !header.isBlank() ? header : UUID.randomUUID().toString();
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponseDTO("INVALID_REQUEST", ex.getMessage(), correlationId));
	}

}
