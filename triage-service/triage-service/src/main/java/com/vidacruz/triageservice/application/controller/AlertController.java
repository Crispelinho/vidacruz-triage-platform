package com.vidacruz.triageservice.application.controller;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
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
import com.vidacruz.triageservice.domain.service.WatchAlertsUseCase;

import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Empty;

/** RF-06: active escalation alerts, acknowledgement and the live SSE feed. */
@RestController
@RequestMapping("/triage/alertas")
public class AlertController {

	private static final Logger log = LoggerFactory.getLogger(AlertController.class);
	private static final int STREAM_BUFFER = 256;
	private static final String EVENT_NAME = "alerta";
	private static final String HEARTBEAT_COMMENT = "latido";

	private final ListActiveAlertsUseCase listActiveAlerts;
	private final AcknowledgeAlertUseCase acknowledgeAlert;
	private final WatchAlertsUseCase watchAlerts;
	private final AlertStreamProperties streamProperties;

	public AlertController(ListActiveAlertsUseCase listActiveAlerts, AcknowledgeAlertUseCase acknowledgeAlert,
			WatchAlertsUseCase watchAlerts, AlertStreamProperties streamProperties) {
		this.listActiveAlerts = listActiveAlerts;
		this.acknowledgeAlert = acknowledgeAlert;
		this.watchAlerts = watchAlerts;
		this.streamProperties = streamProperties;
	}

	@GetMapping
	public Flux<AlertResponse> active() {
		return listActiveAlerts.list().map(AlertMapper::toResponse);
	}

	@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<AlertResponse>> stream() {
		Empty<Void> overflow = Sinks.empty();
		Flux<ServerSentEvent<AlertResponse>> alerts = watchAlerts.watch()
				.map(AlertMapper::toResponse)
				.map(response -> ServerSentEvent.builder(response).event(EVENT_NAME).build())
				.onBackpressureBuffer(STREAM_BUFFER,
						dropped -> {
							log.warn("Alert stream client too slow; closing its stream");
							overflow.tryEmitEmpty();
						},
						BufferOverflowStrategy.DROP_LATEST);
		Flux<ServerSentEvent<AlertResponse>> heartbeat = Flux.interval(streamProperties.heartbeat())
				.map(tick -> ServerSentEvent.<AlertResponse>builder().comment(HEARTBEAT_COMMENT).build());

		return Flux.merge(alerts, heartbeat)
				.takeUntilOther(overflow.asMono())
				.doOnSubscribe(subscription -> log.debug("Alert stream opened"))
				.doFinally(signal -> log.debug("Alert stream closed: {}", signal));
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
