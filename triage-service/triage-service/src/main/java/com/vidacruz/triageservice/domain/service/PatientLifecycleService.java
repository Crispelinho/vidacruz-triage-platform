package com.vidacruz.triageservice.domain.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import com.vidacruz.triageservice.domain.dto.PatientDetailResponse;
import com.vidacruz.triageservice.domain.exception.InvalidStateTransitionException;
import com.vidacruz.triageservice.domain.exception.PatientNotFoundException;
import com.vidacruz.triageservice.domain.model.AuditEntry;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.PatientSnapshot;
import com.vidacruz.triageservice.domain.port.AuditStore;
import com.vidacruz.triageservice.domain.port.OutboxWriter;
import com.vidacruz.triageservice.domain.port.PatientStore;
import com.vidacruz.triageservice.domain.port.TransactionalExecutor;

import reactor.core.publisher.Mono;

/** RF-02 clinical state flow with RN-04 audit and EVT-03/EVT-04 outbox rows. */
public class PatientLifecycleService {

	private static final Logger log = Logger.getLogger(PatientLifecycleService.class.getName());

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

	private final PatientStore patientStore;
	private final AuditStore auditStore;
	private final OutboxWriter outboxWriter;
	private final MedicalAlertRepository alertRepository;
	private final TransactionalExecutor transactions;
	private final Clock clock;

	public PatientLifecycleService(PatientStore patientStore,
			AuditStore auditStore,
			OutboxWriter outboxWriter,
			MedicalAlertRepository alertRepository,
			TransactionalExecutor transactions,
			Clock clock) {
		this.patientStore = patientStore;
		this.auditStore = auditStore;
		this.outboxWriter = outboxWriter;
		this.alertRepository = alertRepository;
		this.transactions = transactions;
		this.clock = clock;
	}

	public Mono<PatientDetailResponse> changeStatus(UUID patientId, String requestedStatus) {
		return patientStore.findById(patientId)
				.switchIfEmpty(Mono.error(new PatientNotFoundException(patientId)))
				.flatMap(patient -> applyTransition(patient, requestedStatus));
	}

	public Mono<PatientDetailResponse> getPatient(UUID patientId) {
		return patientStore.findById(patientId)
				.switchIfEmpty(Mono.error(new PatientNotFoundException(patientId)))
				.map(patient -> toResponse(patient, clock.instant()));
	}

	private Mono<PatientDetailResponse> applyTransition(PatientSnapshot patient, String requestedStatus) {
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

		Mono<?> atomicWrite = patientStore
				.changeStatus(patient.id(), previousStatus, requestedStatus, changedAt)
				.flatMap(updated -> updated == 1
						? Mono.empty()
						: Mono.error(new InvalidStateTransitionException(
								previousStatus + " no puede pasar a " + requestedStatus)))
				.then(auditStore.append(new AuditEntry(
						UUID.randomUUID(), patient.id(), changedAt, previousStatus,
						requestedStatus, auditType)))
				.then(outboxWriter.write(patient.id(), eventType, payload, changedAt))
				// Entering consultation or discharge retires the alert in the same transaction (RF-06, DER-03).
				.then(alertRepository.resolveForPatient(patient.id(), changedAt));

		return transactions.transactional(atomicWrite)
				.thenReturn(toResponse(patient, changedAt, changedAt, requestedStatus))
				.doOnNext(response -> log.info(() -> "Patient " + patient.id() + " moved from "
						+ previousStatus + " to " + requestedStatus));
	}

	private static PatientDetailResponse toResponse(PatientSnapshot patient, Instant statusStart, Instant now,
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

	private static PatientDetailResponse toResponse(PatientSnapshot patient, Instant now) {
		// The timer follows the status start, not the last mutation: escalation stays in the same status.
		return toResponse(patient, patient.statusSince(), now, patient.status());
	}
}
