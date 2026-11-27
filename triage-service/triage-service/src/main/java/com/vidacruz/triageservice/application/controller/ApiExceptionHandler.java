package com.vidacruz.triageservice.application.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import com.vidacruz.triageservice.application.dto.ApiError;
import com.vidacruz.triageservice.domain.exception.AlertConflictException;
import com.vidacruz.triageservice.domain.exception.AlertNotFoundException;
import com.vidacruz.triageservice.domain.exception.IdempotencyKeyReusedException;
import com.vidacruz.triageservice.domain.exception.InvalidAlertRequestException;
import com.vidacruz.triageservice.domain.exception.InvalidPatientDataException;
import com.vidacruz.triageservice.domain.exception.InvalidReportRequestException;
import com.vidacruz.triageservice.domain.exception.InvalidStateTransitionException;
import com.vidacruz.triageservice.domain.exception.PatientNotFoundException;

import reactor.core.publisher.Mono;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final String CORRELATION_HEADER = "X-Correlation-Id";

	@ExceptionHandler(InvalidPatientDataException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Mono<ApiError> handleInvalidPatientData(InvalidPatientDataException exception,
			ServerWebExchange exchange) {
		String correlationId = correlationId(exchange);
		ApiError body = new ApiError("VALIDATION_ERROR", exception.getMessage(), exception.errors(), correlationId);
		return withCorrelation(exchange, body);
	}

	@ExceptionHandler(InvalidReportRequestException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Mono<ApiError> handleInvalidReportRequest(InvalidReportRequestException exception) {
		return Mono.just(new ApiError(exception.code(), exception.getMessage(), exception.field()));
	}

	@ExceptionHandler(InvalidStateTransitionException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public Mono<ApiError> handleInvalidTransition(InvalidStateTransitionException exception,
			ServerWebExchange exchange) {
		return withCorrelation(exchange,
				new ApiError("INVALID_TRANSITION", exception.getMessage(), List.of(), correlationId(exchange)));
	}

	@ExceptionHandler(IdempotencyKeyReusedException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public Mono<ApiError> handleIdempotencyKeyReused(IdempotencyKeyReusedException exception,
			ServerWebExchange exchange) {
		return withCorrelation(exchange,
				new ApiError("IDEMPOTENCY_KEY_REUSED", exception.getMessage(), List.of(), correlationId(exchange)));
	}

	@ExceptionHandler(PatientNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Mono<ApiError> handlePatientNotFound(PatientNotFoundException exception,
			ServerWebExchange exchange) {
		return withCorrelation(exchange,
				new ApiError("PATIENT_NOT_FOUND", exception.getMessage(), List.of(), correlationId(exchange)));
	}

	private static String correlationId(ServerWebExchange exchange) {
		String supplied = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
		return supplied == null || supplied.isBlank() ? UUID.randomUUID().toString() : supplied;
	}

	private static Mono<ApiError> withCorrelation(ServerWebExchange exchange, ApiError body) {
		ServerHttpResponse response = exchange.getResponse();
		response.getHeaders().set(CORRELATION_HEADER, body.correlationId());
		return Mono.just(body);
	}

	@ExceptionHandler(AlertNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Mono<ApiError> handleAlertNotFound(AlertNotFoundException exception) {
		return Mono.just(new ApiError("TRG-008", exception.getMessage()));
	}

	@ExceptionHandler(AlertConflictException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public Mono<ApiError> handleAlertConflict(AlertConflictException exception) {
		return Mono.just(new ApiError("TRG-009", exception.getMessage()));
	}

	@ExceptionHandler(InvalidAlertRequestException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Mono<ApiError> handleInvalidAlertRequest(InvalidAlertRequestException exception) {
		return Mono.just(new ApiError(InvalidAlertRequestException.CODE, exception.getMessage(), exception.field()));
	}
}
