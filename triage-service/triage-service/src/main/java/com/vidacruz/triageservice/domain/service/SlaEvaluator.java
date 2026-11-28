package com.vidacruz.triageservice.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vidacruz.triageservice.domain.model.AlertStatus;
import com.vidacruz.triageservice.domain.model.AuditEntry;
import com.vidacruz.triageservice.domain.model.AuditLog;
import com.vidacruz.triageservice.domain.model.EventOutbox;
import com.vidacruz.triageservice.domain.model.MedicalAlert;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.Patient;
import com.vidacruz.triageservice.domain.model.PatientStore;
import com.vidacruz.triageservice.domain.model.TransactionRunner;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.model.TriageMetrics;

import reactor.core.publisher.Mono;

/**
 * RF-03/RN-03 SLA evaluator: conditional escalation with one audit row, one EVT-02 and one RF-06 alert per patient.
 * All four writes share the same transaction, so an alert exists if and only if the escalation was committed.
 */
public class SlaEvaluator {

	private static final Logger log = LoggerFactory.getLogger(SlaEvaluator.class);

	public static final String ESCALATED_EVENT = "paciente_escalado";
	public static final String ESCALATION_AUDIT = "ESCALAMIENTO";

	private final PatientStore patients;
	private final AuditLog auditLog;
	private final EventOutbox outbox;
	private final MedicalAlertRepository alertRepository;
	private final TransactionRunner transactions;
	private final Clock clock;
	private final TriageMetrics metrics;

	public SlaEvaluator(PatientStore patients,
			AuditLog auditLog,
			EventOutbox outbox,
			MedicalAlertRepository alertRepository,
			TransactionRunner transactions,
			Clock clock,
			TriageMetrics metrics) {
		this.patients = patients;
		this.auditLog = auditLog;
		this.outbox = outbox;
		this.alertRepository = alertRepository;
		this.transactions = transactions;
		this.clock = clock;
		this.metrics = metrics;
	}

	/** Returns the number of patients escalated by this run. */
	public Mono<Integer> evaluateOnce() {
		return patients.findEscalationCandidates()
				.flatMap(this::escalateIfOverdue)
				.reduce(0, Integer::sum);
	}

	private Mono<Integer> escalateIfOverdue(Patient patient) {
		// PostgreSQL TIMESTAMPTZ has microsecond precision. Comparing a nanosecond clock against the stored instant
		// would escalate up to one microsecond before the real SLA boundary (AMB-04).
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		Instant registeredAt = patient.registeredAt().truncatedTo(ChronoUnit.MICROS);
		Instant expiresAt = registeredAt.plusSeconds(patient.slaMinutes() * 60L);
		if (!now.isAfter(expiresAt)) {
			return Mono.just(0);
		}
		return escalate(patient, now);
	}

	private Mono<Integer> escalate(Patient patient, Instant changedAt) {
		TriageLevel level = patient.level();
		Mono<Integer> atomicWrite = patients.markEscalated(patient.id(), changedAt)
				.flatMap(escalated -> {
					if (!escalated) {
						return Mono.just(0);
					}
					Map<String, Object> payload = Map.of(
							"nivel", level.name(),
							"slaMinutos", patient.slaMinutes(),
							"fechaEscalamiento", changedAt.toString());
					return auditLog.append(new AuditEntry(
							UUID.randomUUID(), patient.id(), changedAt,
							PatientLifecycleService.REGISTERED,
							PatientLifecycleService.REGISTERED,
							ESCALATION_AUDIT))
							.then(outbox.append(patient.id(), ESCALATED_EVENT, payload, changedAt))
							.then(alertRepository.create(alert(patient, changedAt)))
							.thenReturn(1);
				});
		return transactions.inTransaction(atomicWrite)
				.doOnNext(count -> {
					if (count == 1) {
						metrics.patientEscalated(level);
						log.info("Patient {} escalated: level {} exceeded its {}-minute SLA", patient.id(), level,
								patient.slaMinutes());
					}
				});
	}

	/** The alert holds the clinical signal only; the patient detail stays behind the regular API. */
	private static MedicalAlert alert(Patient patient, Instant changedAt) {
		var wait = BigDecimal.valueOf(Duration.between(patient.registeredAt(), changedAt).toSeconds())
				.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
		return new MedicalAlert(UUID.randomUUID(), patient.id(), patient.level(),
				patient.slaMinutes(), patient.registeredAt(), changedAt, wait,
				AlertStatus.PENDIENTE, null, null, null, changedAt);
	}
}
