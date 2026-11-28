package com.vidacruz.triageservice.domain.port;

import java.time.Instant;
import java.util.UUID;

import com.vidacruz.triageservice.domain.model.PatientSnapshot;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Persistence boundary for the patient aggregate used by triage use cases. */
public interface PatientStore {

	Mono<PatientSnapshot> findById(UUID id);

	Flux<PatientSnapshot> findEscalationCandidates();

	Mono<PatientSnapshot> save(PatientSnapshot patient);

	Mono<Integer> changeStatus(UUID id, String fromStatus, String toStatus, Instant changedAt);

	Mono<Integer> escalate(UUID id, Instant changedAt);
}
