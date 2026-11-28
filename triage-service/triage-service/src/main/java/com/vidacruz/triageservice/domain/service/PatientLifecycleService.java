package com.vidacruz.triageservice.domain.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vidacruz.triageservice.domain.exception.InvalidStateTransitionException;
import com.vidacruz.triageservice.domain.exception.PatientNotFoundException;
import com.vidacruz.triageservice.domain.model.AuditEntry;
import com.vidacruz.triageservice.domain.model.AuditLog;
import com.vidacruz.triageservice.domain.model.EventOutbox;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.Patient;
import com.vidacruz.triageservice.domain.model.PatientDetail;
import com.vidacruz.triageservice.domain.model.PatientStore;
import com.vidacruz.triageservice.domain.model.TransactionRunner;

import reactor.core.publisher.Mono;

/** RF-02 clinical state flow with RN-04 audit and EVT-03/EVT-04 outbox rows. */
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

	private final PatientStore patients;
	private final AuditLog auditLog;
	private final EventOutbox outbox;
	private final MedicalAlertRepository alertRepository;
	private final TransactionRunner transactions;
	private final Clock clock;

	public PatientLifecycleService(PatientStore patients,
			AuditLog auditLog,
			EventOutbox outbox,
			MedicalAlertRepository alertRepository,
			TransactionRunner transactions,
			Clock clock) {
		this.patients = patients;
		this.auditLog = auditLog;
		this.outbox = outbox;
		this.alertRepository = alertRepository;
		this.transactions = transactions;
		this.clock = clock;
	}

	public Mono<PatientDetail> changeStatus(UUID patientId, String requestedStatus) {
		return patients.findById(patientId)
				.switchIfEmpty(Mono.error(new PatientNotFoundException(patientId)))
				.flatMap(patient -> applyTransition(patient, requestedStatus));
	}

	public Mono<PatientDetail> getPatient(UUID patientId) {
		return patients.findById(patientId)
				.switchIfEmpty(Mono.error(new PatientNotFoundException(patientId)))
				.map(patient -> patient.detailAt(clock.instant()));
	}

	private Mono<PatientDetail> applyTransition(Patient patient, String requestedStatus) {
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

		Mono<?> atomicWrite = patients
				.changeStatus(patient.id(), previousStatus, requestedStatus, changedAt)
				.flatMap(moved -> moved
						? Mono.empty()
						: Mono.error(new InvalidStateTransitionException(
								previousStatus + " no puede pasar a " + requestedStatus)))
				.then(auditLog.append(new AuditEntry(
						UUID.randomUUID(), patient.id(), changedAt, previousStatus,
						requestedStatus, auditType)))
				.then(outbox.append(patient.id(), eventType, payload, changedAt))
				// Entering consultation or discharge retires the alert in the same transaction (RF-06, DER-03).
				.then(alertRepository.resolveForPatient(patient.id(), changedAt));

		return transactions.inTransaction(atomicWrite)
				.then(Mono.fromSupplier(() -> patient.detailAfterTransition(requestedStatus, changedAt)))
				.doOnNext(detail -> log.info("Patient {} moved from {} to {}", patient.id(), previousStatus,
						requestedStatus));
	}
}
