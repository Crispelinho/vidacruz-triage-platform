package com.vidacruz.triageservice.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.vidacruz.triageservice.domain.model.Patient;
import com.vidacruz.triageservice.domain.model.PatientStore;
import com.vidacruz.triageservice.domain.model.TriageLevel;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** R2DBC adapter for {@link PatientStore}; maps the {@code paciente} row to the domain model. */
@Repository
public class R2dbcPatientStore implements PatientStore {

	private final PatientRepository repository;

	public R2dbcPatientStore(PatientRepository repository) {
		this.repository = repository;
	}

	@Override
	public Mono<Void> add(Patient patient) {
		return repository.save(toEntity(patient)).then();
	}

	@Override
	public Mono<Patient> findById(UUID id) {
		return repository.findById(id).map(R2dbcPatientStore::toDomain);
	}

	@Override
	public Flux<Patient> findEscalationCandidates() {
		return repository.findEscalationCandidates().map(R2dbcPatientStore::toDomain);
	}

	@Override
	public Mono<Boolean> changeStatus(UUID id, String expectedStatus, String nextStatus, Instant changedAt) {
		return wonUpdate(repository.changeStatus(id, expectedStatus, nextStatus, changedAt));
	}

	@Override
	public Mono<Boolean> markEscalated(UUID id, Instant changedAt) {
		return wonUpdate(repository.escalate(id, changedAt));
	}

	/** R2DBC @Query UPDATEs complete empty on success in this stack; only an explicit count other than 1 lost. */
	private static Mono<Boolean> wonUpdate(Mono<Integer> updated) {
		return updated.map(count -> count == 1).defaultIfEmpty(true);
	}

	static PatientEntity toEntity(Patient patient) {
		return new PatientEntity(
				patient.id(),
				patient.fullName(),
				patient.age(),
				patient.heartRate(),
				patient.systolic(),
				patient.diastolic(),
				patient.spo2(),
				patient.temperature(),
				patient.pain(),
				patient.gcs(),
				patient.complaint(),
				patient.level().name(),
				patient.slaMinutes(),
				patient.status(),
				patient.escalated(),
				patient.registeredAt(),
				patient.lastChangedAt(),
				patient.statusSince());
	}

	static Patient toDomain(PatientEntity entity) {
		return new Patient(
				entity.id(),
				entity.fullName(),
				entity.age(),
				entity.heartRate(),
				entity.systolic(),
				entity.diastolic(),
				entity.spo2(),
				entity.temperature(),
				entity.pain(),
				entity.gcs(),
				entity.complaint(),
				TriageLevel.valueOf(entity.level()),
				entity.slaMinutes(),
				entity.status(),
				Boolean.TRUE.equals(entity.escalated()),
				entity.registeredAt(),
				entity.lastChangedAt(),
				entity.statusSince());
	}
}
