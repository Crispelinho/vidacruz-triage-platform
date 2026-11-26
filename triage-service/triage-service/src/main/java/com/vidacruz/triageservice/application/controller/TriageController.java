package com.vidacruz.triageservice.application.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vidacruz.triageservice.application.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.application.dto.PatientRegistrationResponse;
import com.vidacruz.triageservice.domain.service.TriageRegistrationService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/triage")
public class TriageController {

	private final TriageRegistrationService registrationService;

	public TriageController(TriageRegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	@PostMapping("/pacientes")
	public Mono<ResponseEntity<PatientRegistrationResponse>> register(
			@RequestBody Mono<PatientRegistrationRequest> body) {
		return body.flatMap(registrationService::register)
				.map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
	}
}
