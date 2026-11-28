package com.vidacruz.triageservice.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import com.vidacruz.triageservice.domain.model.AuditEntry;
import com.vidacruz.triageservice.domain.model.AlertStatus;
import com.vidacruz.triageservice.domain.model.MedicalAlert;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.PatientSnapshot;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.model.TriageMetrics;
import com.vidacruz.triageservice.domain.port.AuditStore;
import com.vidacruz.triageservice.domain.port.OutboxWriter;
import com.vidacruz.triageservice.domain.port.PatientStore;
import com.vidacruz.triageservice.domain.port.TransactionalExecutor;

import reactor.core.publisher.Mono;

/**
 * RF-03/RN-03 SLA evaluator: conditional escalation with one audit row, one EVT-02 and one RF-06 alert per patient.
 * All four writes share the same transaction, so an alert exists if and only if the escalation was committed.
 */
public class SlaEvaluator {

	private static final Logger log = Logger.getLogger(SlaEvaluator.class.getName());

	public static final String ESCALATED_EVENT = "paciente_escalado";
	public static final String ESCALATION_AUDIT = "ESCALAMIENTO";

	private final PatientStore patientStore;
	private final AuditStore auditStore;
	private final OutboxWriter outboxWriter;
	private final MedicalAlertRepository alertRepository;
	private final TransactionalExecutor transactions;
	private final Clock clock;
	private final TriageMetrics metrics;

	public SlaEvaluator(PatientStore patientStore,
			AuditStore auditStore,
			OutboxWriter outboxWriter,
			MedicalAlertRepository alertRepository,
			TransactionalExecutor transactions,
			Clock clock,
			TriageMetrics metrics) {
		this.patientStore = patientStore;
		this.auditStore = auditStore;
		this.outboxWriter = outboxWriter;
		this.alertRepository = alertRepository;
		this.transactions = transactions;
		this.clock = clock;
		this.metrics = metrics;
	}

	/** Returns the number of patients escalated by this run. */
	public Mono<Integer> evaluateOnce() {
		return patientStore.findEscalationCandidates()
				.flatMap(this::escalateIfOverdue)
				.reduce(0, Integer::sum);
	}

	private Mono<Integer> escalateIfOverdue(PatientSnapshot patient) {
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

	private Mono<Integer> escalate(PatientSnapshot patient, Instant changedAt) {
		TriageLevel level = TriageLevel.valueOf(patient.level());
		Mono<Integer> atomicWrite = patientStore.escalate(patient.id(), changedAt)
				// R2DBC @Query UPDATEs complete empty on success in this stack; only an explicit 0 is a lost race.
				.defaultIfEmpty(1)
				.flatMap(updated -> {
					if (updated != 1) {
						return Mono.just(0);
					}
					Map<String, Object> payload = Map.of(
							"nivel", patient.level(),
							"slaMinutos", patient.slaMinutes(),
							"fechaEscalamiento", changedAt.toString());
					return auditStore.append(new AuditEntry(
							UUID.randomUUID(), patient.id(), changedAt,
							PatientLifecycleService.REGISTERED,
							PatientLifecycleService.REGISTERED,
							ESCALATION_AUDIT))
							.then(outboxWriter.write(patient.id(), ESCALATED_EVENT, payload, changedAt))
							.then(alertRepository.create(alert(patient, changedAt)))
							.thenReturn(1);
				});
		return transactions.transactional(atomicWrite)
				.doOnNext(count -> {
					if (count == 1) {
						metrics.patientEscalated(level);
						log.info(() -> "Patient " + patient.id() + " escalated: level " + level
								+ " exceeded its " + patient.slaMinutes() + "-minute SLA");
					}
				});
	}

	/** The alert holds the clinical signal only; the patient detail stays behind the regular API. */
	private static MedicalAlert alert(PatientSnapshot patient, Instant changedAt) {
		var wait = BigDecimal.valueOf(Duration.between(patient.registeredAt(), changedAt).toSeconds())
				.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
		return new MedicalAlert(UUID.randomUUID(), patient.id(), TriageLevel.valueOf(patient.level()),
				patient.slaMinutes(), patient.registeredAt(), changedAt, wait,
				AlertStatus.PENDIENTE, null, null, null, changedAt);
	}
}
