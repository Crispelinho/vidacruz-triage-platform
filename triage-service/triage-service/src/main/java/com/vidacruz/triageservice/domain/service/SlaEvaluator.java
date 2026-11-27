package com.vidacruz.triageservice.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.vidacruz.triageservice.domain.model.AlertStatus;
import com.vidacruz.triageservice.domain.model.MedicalAlert;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.TriageMetrics;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventWriter;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryEntity;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryRepository;
import com.vidacruz.triageservice.infrastructure.persistence.PatientEntity;
import com.vidacruz.triageservice.infrastructure.persistence.PatientRepository;

import reactor.core.publisher.Mono;

/**
 * RF-03/RN-03 SLA evaluator: conditional escalation with one audit row, one EVT-02 and one RF-06 alert per patient.
 * All four writes share the same transaction, so an alert exists if and only if the escalation was committed.
 */
@Service
public class SlaEvaluator {

	public static final String ESCALATED_EVENT = "paciente_escalado";
	public static final String ESCALATION_AUDIT = "ESCALAMIENTO";

	private final PatientRepository patientRepository;
	private final AuditEntryRepository auditRepository;
	private final OutboxEventWriter eventWriter;
	private final MedicalAlertRepository alertRepository;
	private final TransactionalOperator transactionalOperator;
	private final Clock clock;
	private final TriageMetrics metrics;

	public SlaEvaluator(PatientRepository patientRepository,
			AuditEntryRepository auditRepository,
			OutboxEventWriter eventWriter,
			MedicalAlertRepository alertRepository,
			TransactionalOperator transactionalOperator,
			Clock clock) {
		this(patientRepository, auditRepository, eventWriter, alertRepository, transactionalOperator, clock, new TriageMetrics() {
			@Override public void patientClassified(TriageLevel level) {}
			@Override public void patientEscalated(TriageLevel level) {}
			@Override public void alertAcknowledged(TriageLevel level) {}
		});
	}

	@Autowired
	public SlaEvaluator(PatientRepository patientRepository,
			AuditEntryRepository auditRepository,
			OutboxEventWriter eventWriter,
			MedicalAlertRepository alertRepository,
			TransactionalOperator transactionalOperator,
			Clock clock,
			TriageMetrics metrics) {
		this.patientRepository = patientRepository;
		this.auditRepository = auditRepository;
		this.eventWriter = eventWriter;
		this.alertRepository = alertRepository;
		this.transactionalOperator = transactionalOperator;
		this.clock = clock;
		this.metrics = metrics;
	}

	/** Returns the number of patients escalated by this run. */
	public Mono<Integer> evaluateOnce() {
		return patientRepository.findEscalationCandidates()
				.flatMap(this::escalateIfOverdue)
				.reduce(0, Integer::sum);
	}

	private Mono<Integer> escalateIfOverdue(PatientEntity patient) {
		Instant now = clock.instant();
		Instant expiresAt = patient.registeredAt().plusSeconds(patient.slaMinutes() * 60L);
		if (!now.isAfter(expiresAt)) {
			return Mono.just(0);
		}
		return escalate(patient, now);
	}

	private Mono<Integer> escalate(PatientEntity patient, Instant changedAt) {
		Mono<Integer> atomicWrite = patientRepository.escalate(patient.id(), changedAt)
				.flatMap(updated -> {
					if (updated != 1) {
						return Mono.just(0);
					}
					Map<String, Object> payload = Map.of(
							"nivel", patient.level(),
							"slaMinutos", patient.slaMinutes(),
							"fechaEscalamiento", changedAt.toString());
					return auditRepository.save(new AuditEntryEntity(
							UUID.randomUUID(), patient.id(), changedAt,
							PatientLifecycleService.REGISTERED,
							PatientLifecycleService.REGISTERED,
							ESCALATION_AUDIT))
							.then(eventWriter.write(patient.id(), ESCALATED_EVENT, payload, changedAt))
							.then(alertRepository.create(alert(patient, changedAt)))
							.thenReturn(1);
				});
		return transactionalOperator.transactional(atomicWrite);
	}

	/** The alert holds the clinical signal only; the patient detail stays behind the regular API. */
	private static MedicalAlert alert(PatientEntity patient, Instant changedAt) {
		var wait = BigDecimal.valueOf(Duration.between(patient.registeredAt(), changedAt).toSeconds())
				.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
		return new MedicalAlert(UUID.randomUUID(), patient.id(), TriageLevel.valueOf(patient.level()),
				patient.slaMinutes(), patient.registeredAt(), changedAt, wait,
				AlertStatus.PENDIENTE, null, null, null, changedAt);
	}
}
