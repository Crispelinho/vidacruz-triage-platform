package com.vidacruz.triageservice.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.vidacruz.triageservice.domain.model.PatientSnapshot;
import com.vidacruz.triageservice.domain.port.PatientStore;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** R2DBC implementation of the patient persistence boundary. */
@Component
public class PatientStoreAdapter implements PatientStore {

	private final PatientRepository repository;

	public PatientStoreAdapter(PatientRepository repository) {
		this.repository = repository;
	}

	@Override
	public Mono<PatientSnapshot> findById(UUID id) {
		return repository.findById(id).map(PatientStoreAdapter::toDomain);
	}

	@Override
	public Flux<PatientSnapshot> findEscalationCandidates() {
		return repository.findEscalationCandidates().map(PatientStoreAdapter::toDomain);
	}

	@Override
	public Mono<PatientSnapshot> save(PatientSnapshot patient) {
		return repository.save(toEntity(patient)).map(PatientStoreAdapter::toDomain);
	}

	@Override
	public Mono<Integer> changeStatus(UUID id, String fromStatus, String toStatus, Instant changedAt) {
		return repository.changeStatus(id, fromStatus, toStatus, changedAt);
	}

	@Override
	public Mono<Integer> escalate(UUID id, Instant changedAt) {
		return repository.escalate(id, changedAt);
	}

	private static PatientSnapshot toDomain(PatientEntity entity) {
		return new PatientSnapshot(
				entity.id(), entity.fullName(), entity.age(), entity.heartRate(), entity.systolic(),
				entity.diastolic(), entity.spo2(), entity.temperature(), entity.pain(), entity.gcs(),
				entity.complaint(), entity.level(), entity.slaMinutes(), entity.status(), entity.escalated(),
				entity.registeredAt(), entity.lastChangedAt(), entity.statusSince());
	}

	private static PatientEntity toEntity(PatientSnapshot patient) {
		return new PatientEntity(
				patient.id(), patient.fullName(), patient.age(), patient.heartRate(), patient.systolic(),
				patient.diastolic(), patient.spo2(), patient.temperature(), patient.pain(), patient.gcs(),
				patient.complaint(), patient.level(), patient.slaMinutes(), patient.status(), patient.escalated(),
				patient.registeredAt(), patient.lastChangedAt(), patient.statusSince());
	}
}
