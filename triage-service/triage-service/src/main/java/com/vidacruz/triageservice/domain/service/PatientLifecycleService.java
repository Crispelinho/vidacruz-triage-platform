package com.vidacruz.triageservice.domain.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.vidacruz.triageservice.application.dto.PatientDetailResponse;
import com.vidacruz.triageservice.domain.exception.InvalidStateTransitionException;
import com.vidacruz.triageservice.domain.exception.PatientNotFoundException;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventWriter;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryEntity;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryRepository;
import com.vidacruz.triageservice.infrastructure.persistence.PatientEntity;
import com.vidacruz.triageservice.infrastructure.persistence.PatientRepository;

import reactor.core.publisher.Mono;

/** RF-02 clinical state flow with RN-04 audit and EVT-03/EVT-04 outbox rows. */
@Service
public class PatientLifecycleService {

	private static final Logger log = LoggerFactory.getLogger(PatientLifecycleService.class);

	public static final String REGISTERED = "REGISTRADO";
	public static final String IN_CONSULTATION = "EN_CONSULTA";
	public static final String DISCHARGED = "ALTA";

	public static final String CONSULTATION_EVENT = "paciente_en_consulta";
	public static final String DISCHARGE_EVENT = "paciente_alta";
	public static final String CONSULTATION_AUDIT = "ENTRADA_CONSULTA";
	public static final String DISCHARGE_AUDIT = "ALTA";

	private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
			REGISTERED, Set.of(IN_CONSULTATION, DISCHARGED),
			IN_CONSULTATION, Set.of(DISCHARGED),
			DISCHARGED, Set.of());

	private final PatientRepository patientRepository;
	private final AuditEntryRepository auditRepository;
	private final OutboxEventWriter eventWriter;
	private final MedicalAlertRepository alertRepository;
	private final TransactionalOperator transactionalOperator;
	private final Clock clock;

	public PatientLifecycleService(PatientRepository patientRepository,
			AuditEntryRepository auditRepository,
			OutboxEventWriter eventWriter,
			MedicalAlertRepository alertRepository,
			TransactionalOperator transactionalOperator,
			Clock clock) {
		this.patientRepository = patientRepository;
		this.auditRepository = auditRepository;
		this.eventWriter = eventWriter;
		this.alertRepository = alertRepository;
		this.transactionalOperator = transactionalOperator;
		this.clock = clock;
	}

	public Mono<PatientDetailResponse> changeStatus(UUID patientId, String requestedStatus) {
		return patientRepository.findById(patientId)
				.switchIfEmpty(Mono.error(new PatientNotFoundException(patientId)))
				.flatMap(patient -> applyTransition(patient, requestedStatus));
	}

	public Mono<PatientDetailResponse> getPatient(UUID patientId) {
		return patientRepository.findById(patientId)
				.switchIfEmpty(Mono.error(new PatientNotFoundException(patientId)))
				.map(patient -> toResponse(patient, clock.instant()));
	}

	private Mono<PatientDetailResponse> applyTransition(PatientEntity patient, String requestedStatus) {
		String previousStatus = patient.status();
		Set<String> allowed = ALLOWED_TRANSITIONS.get(previousStatus);
		if (allowed == null || requestedStatus == null || !allowed.contains(requestedStatus)) {
			return Mono.error(new InvalidStateTransitionException(
					previousStatus + " no puede pasar a " + requestedStatus));
		}

		Instant changedAt = clock.instant();
		boolean consultation = IN_CONSULTATION.equals(requestedStatus);
		String eventType = consultation ? CONSULTATION_EVENT : DISCHARGE_EVENT;
		String auditType = consultation ? CONSULTATION_AUDIT : DISCHARGE_AUDIT;
		Map<String, Object> payload = consultation
				? Map.of("estadoAnterior", previousStatus, "fechaEntradaConsulta", changedAt.toString())
				: Map.of("estadoAnterior", previousStatus, "fechaAlta", changedAt.toString());

		Mono<?> atomicWrite = patientRepository
				.changeStatus(patient.id(), previousStatus, requestedStatus, changedAt)
				.flatMap(updated -> updated == 1
						? Mono.empty()
						: Mono.error(new InvalidStateTransitionException(
								previousStatus + " no puede pasar a " + requestedStatus)))
				.then(auditRepository.save(new AuditEntryEntity(
						UUID.randomUUID(), patient.id(), changedAt, previousStatus,
						requestedStatus, auditType)))
				.then(eventWriter.write(patient.id(), eventType, payload, changedAt))
				// Entering consultation or discharge retires the alert in the same transaction (RF-06, DER-03).
				.then(alertRepository.resolveForPatient(patient.id(), changedAt));

		return transactionalOperator.transactional(atomicWrite)
				.thenReturn(toResponse(patient, changedAt, changedAt, requestedStatus))
				.doOnNext(response -> log.info("Patient {} moved from {} to {}", patient.id(), previousStatus,
						requestedStatus));
	}

	private static PatientDetailResponse toResponse(PatientEntity patient, Instant statusStart, Instant now,
			String status) {
		long minutes = Duration.between(statusStart, now).toMinutes();
		return new PatientDetailResponse(
				patient.id(),
				patient.fullName(),
				patient.level(),
				patient.slaMinutes(),
				status,
				Boolean.TRUE.equals(patient.escalated()),
				patient.registeredAt(),
				minutes,
				patient.age(),
				patient.heartRate(),
				patient.systolic(),
				patient.diastolic(),
				patient.spo2(),
				patient.temperature(),
				patient.pain(),
				patient.gcs(),
				patient.complaint());
	}

	private static PatientDetailResponse toResponse(PatientEntity patient, Instant now) {
		// The timer follows the status start, not the last mutation: escalation stays in the same status.
		return toResponse(patient, patient.statusSince(), now, patient.status());
	}
}
