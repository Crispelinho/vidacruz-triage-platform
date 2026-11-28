package com.vidacruz.triageservice.domain.model;

import java.time.Instant;
import java.util.UUID;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Persistence port for patients. Conditional updates report whether this caller won the change. */
public interface PatientStore {

	Mono<Void> add(Patient patient);

	Mono<Patient> findById(UUID id);

	/** Patients still waiting in {@code REGISTRADO} that have not been escalated yet (RN-03). */
	Flux<Patient> findEscalationCandidates();

	/** Moves the patient only if it is still in {@code expectedStatus}; {@code false} means a concurrent change won. */
	Mono<Boolean> changeStatus(UUID id, String expectedStatus, String nextStatus, Instant changedAt);

	/** Flags the patient as escalated once; {@code false} means it was already escalated or left the queue. */
	Mono<Boolean> markEscalated(UUID id, Instant changedAt);
}
