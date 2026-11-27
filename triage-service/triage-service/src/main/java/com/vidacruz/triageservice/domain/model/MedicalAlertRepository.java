package com.vidacruz.triageservice.domain.model;

import java.time.Instant;
import java.util.UUID;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * RF-06 storage port. Every write is executed inside the caller's Reactor transaction so an alert exists if and only
 * if the escalation (or the patient transition that resolves it) was committed.
 */
public interface MedicalAlertRepository {

	/** Inserts one alert per patient; a repeated escalation is a no-op (DER-03). */
	Mono<Void> create(MedicalAlert alert);

	/** Active alerts, most severe first and then oldest first. */
	Flux<MedicalAlert> findActive();

	Mono<MedicalAlert> findById(UUID id);

	/** Takes a pending alert. Returns false when it was already taken or resolved. */
	Mono<Boolean> acknowledge(UUID id, String responsible, Instant at);

	/** Resolves the active alert of a patient when they enter consultation or are discharged. */
	Mono<Boolean> resolveForPatient(UUID patientId, Instant at);
}
