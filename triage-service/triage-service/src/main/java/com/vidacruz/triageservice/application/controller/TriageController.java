package com.vidacruz.triageservice.application.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vidacruz.triageservice.application.dto.PatientDetailResponse;
import com.vidacruz.triageservice.application.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.application.dto.PatientRegistrationResponse;
import com.vidacruz.triageservice.application.dto.StateChangeRequest;
import com.vidacruz.triageservice.domain.service.PatientLifecycleService;
import com.vidacruz.triageservice.domain.service.TriageRegistrationService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/triage")
public class TriageController {

	private final TriageRegistrationService registrationService;
	private final PatientLifecycleService lifecycleService;

	public TriageController(TriageRegistrationService registrationService,
			PatientLifecycleService lifecycleService) {
		this.registrationService = registrationService;
		this.lifecycleService = lifecycleService;
	}

	@PostMapping("/pacientes")
	public Mono<ResponseEntity<PatientRegistrationResponse>> register(
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody Mono<PatientRegistrationRequest> body) {
		// toDomain() validates; an invalid body fails here, before any idempotency key is reserved.
		return body.flatMap(request -> registrationService.register(request.toDomain(), idempotencyKey))
				.map(result -> ResponseEntity.status(HttpStatus.CREATED).body(PatientMapper.toResponse(result)));
	}

	@PatchMapping("/pacientes/{id}/estado")
	public Mono<PatientDetailResponse> changeStatus(@PathVariable UUID id,
			@RequestBody Mono<StateChangeRequest> body) {
		return body.flatMap(request -> lifecycleService.changeStatus(id, request.status()))
				.map(PatientMapper::toResponse);
	}

	@GetMapping("/pacientes/{id}")
	public Mono<PatientDetailResponse> getPatient(@PathVariable UUID id) {
		return lifecycleService.getPatient(id).map(PatientMapper::toResponse);
	}
}
