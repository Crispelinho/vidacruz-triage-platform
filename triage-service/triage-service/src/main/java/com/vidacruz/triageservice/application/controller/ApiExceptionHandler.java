package com.vidacruz.triageservice.application.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.vidacruz.triageservice.application.dto.ApiError;
import com.vidacruz.triageservice.domain.exception.InvalidPatientDataException;
import com.vidacruz.triageservice.domain.exception.InvalidStateTransitionException;
import com.vidacruz.triageservice.domain.exception.PatientNotFoundException;

import reactor.core.publisher.Mono;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(InvalidPatientDataException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Mono<ApiError> handleInvalidPatientData(InvalidPatientDataException exception) {
		return Mono.just(new ApiError("VALIDATION_ERROR", exception.getMessage()));
	}

	@ExceptionHandler(InvalidStateTransitionException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public Mono<ApiError> handleInvalidTransition(InvalidStateTransitionException exception) {
		return Mono.just(new ApiError("INVALID_TRANSITION", exception.getMessage()));
	}

	@ExceptionHandler(PatientNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Mono<ApiError> handlePatientNotFound(PatientNotFoundException exception) {
		return Mono.just(new ApiError("PATIENT_NOT_FOUND", exception.getMessage()));
	}
}
