package com.vidacruz.triageservice.application.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vidacruz.triageservice.application.dto.AcknowledgeAlertRequest;
import com.vidacruz.triageservice.application.dto.AlertResponse;
import com.vidacruz.triageservice.domain.exception.InvalidAlertRequestException;
import com.vidacruz.triageservice.domain.service.AcknowledgeAlertUseCase;
import com.vidacruz.triageservice.domain.service.ListActiveAlertsUseCase;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** RF-06: active escalation alerts and acknowledgement by the medical team. */
@RestController
@RequestMapping("/triage/alertas")
public class AlertController {

	private final ListActiveAlertsUseCase listActiveAlerts;
	private final AcknowledgeAlertUseCase acknowledgeAlert;

	public AlertController(ListActiveAlertsUseCase listActiveAlerts, AcknowledgeAlertUseCase acknowledgeAlert) {
		this.listActiveAlerts = listActiveAlerts;
		this.acknowledgeAlert = acknowledgeAlert;
	}

	@GetMapping
	public Flux<AlertResponse> active() {
		return listActiveAlerts.list().map(AlertMapper::toResponse);
	}

	@PostMapping("/{id}/reconocimiento")
	public Mono<AlertResponse> acknowledge(@PathVariable String id,
			@RequestBody Mono<AcknowledgeAlertRequest> body) {
		return body.flatMap(request -> acknowledgeAlert
				.acknowledge(parseId(id), request.responsible(), Instant.now()))
				.map(AlertMapper::toResponse);
	}

	private static UUID parseId(String id) {
		try {
			return UUID.fromString(id);
		} catch (IllegalArgumentException error) {
			throw new InvalidAlertRequestException("id", "El id de la alerta debe ser un UUID");
		}
	}
}
