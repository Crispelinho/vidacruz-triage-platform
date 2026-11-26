package com.vidacruz.triageservice.domain.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.vidacruz.triageservice.event.infrastructure.OutboxEventWriter;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryEntity;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryRepository;
import com.vidacruz.triageservice.infrastructure.persistence.PatientEntity;
import com.vidacruz.triageservice.infrastructure.persistence.PatientRepository;

import reactor.core.publisher.Mono;

/** RF-03/RN-03 SLA evaluator: conditional escalation with one audit row and one EVT-02 per patient. */
@Service
public class SlaEvaluator {

	public static final String ESCALATED_EVENT = "paciente_escalado";
	public static final String ESCALATION_AUDIT = "ESCALAMIENTO";

	private final PatientRepository patientRepository;
	private final AuditEntryRepository auditRepository;
	private final OutboxEventWriter eventWriter;
	private final TransactionalOperator transactionalOperator;

	public SlaEvaluator(PatientRepository patientRepository,
			AuditEntryRepository auditRepository,
			OutboxEventWriter eventWriter,
			TransactionalOperator transactionalOperator) {
		this.patientRepository = patientRepository;
		this.auditRepository = auditRepository;
		this.eventWriter = eventWriter;
		this.transactionalOperator = transactionalOperator;
	}

	/** Returns the number of patients escalated by this run. */
	public Mono<Integer> evaluateOnce() {
		return patientRepository.findEscalationCandidates()
				.flatMap(this::escalateIfOverdue)
				.reduce(0, Integer::sum);
	}

	private Mono<Integer> escalateIfOverdue(PatientEntity patient) {
		Instant now = Instant.now();
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
							.thenReturn(1);
				});
		return transactionalOperator.transactional(atomicWrite);
	}
}
