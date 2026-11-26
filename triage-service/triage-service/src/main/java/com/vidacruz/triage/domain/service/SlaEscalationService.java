package com.vidacruz.triage.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triage.domain.model.PatientState;
import com.vidacruz.triage.infrastructure.persistence.PatientStateRepository;
import com.vidacruz.triage.infrastructure.persistence.SlaEscalationRepository;
import com.vidacruz.triage.infrastructure.persistence.SlaEscalationRepository.Candidate;
import com.vidacruz.triage.infrastructure.persistence.SlaEscalationRepository.EscalatedPatient;
import com.vidacruz.triageservice.event.domain.OutboxEvento;
import com.vidacruz.triageservice.event.domain.PacienteEscalado;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * RF-03 / RN-03: escalates overdue REGISTRADO patients once. Each patient is handled in its own R2DBC
 * transaction: the guarded flag update, the ESCALAMIENTO audit row (RN-04) and the EVT-02 outbox row commit
 * together or not at all (DER-02/DER-03). Only the evaluator whose UPDATE changes the row writes audit and
 * event, so any number of replicas may run this concurrently without leader election (ADR-13). Kafka is
 * never called here (ADR-03).
 */
@Service
public class SlaEscalationService {

	private static final Logger log = LoggerFactory.getLogger(SlaEscalationService.class);

	static final String EVT_02_TYPE = "paciente_escalado";
	/** Audit {@code tipo_evento} value (G-T13). */
	static final String AUDIT_ESCALAMIENTO = "ESCALAMIENTO";
	static final int SCHEMA_VERSION = 1;

	private final SlaEscalationRepository escalations;
	private final PatientStateRepository patients;
	private final R2dbcEntityTemplate template;
	private final TransactionalOperator transaction;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public SlaEscalationService(SlaEscalationRepository escalations, PatientStateRepository patients,
			R2dbcEntityTemplate template, TransactionalOperator transaction, ObjectMapper objectMapper,
			Clock clock) {
		this.escalations = escalations;
		this.patients = patients;
		this.template = template;
		this.transaction = transaction;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	/**
	 * One evaluation pass. Emits the number of patients this call escalated. A failure on one patient is
	 * logged and rolled back for that patient only; it is retried on the next pass (ADR-13).
	 */
	public Mono<Long> evaluate() {
		return Mono.defer(() -> {
			LocalDateTime now = LocalDateTime.now(clock);
			return escalations.findOverdue(now)
					.concatMap(candidate -> escalateOne(candidate, now)
							.onErrorResume(e -> {
								log.error("SLA escalation failed for patient {}; will retry on the next run",
										candidate.id(), e);
								return Mono.just(false);
							}))
					.filter(Boolean::booleanValue)
					.count();
		});
	}

	/** Emits true when this call won the guarded update, false when another evaluator or a transition did. */
	private Mono<Boolean> escalateOne(Candidate candidate, LocalDateTime now) {
		return escalations.escalate(candidate.id(), now)
				.flatMap(updated -> {
					ZonedDateTime occurredAt = now.atZone(ZoneOffset.UTC);
					return patients.insertAudit(candidate.id(), PatientState.REGISTRADO, PatientState.REGISTRADO,
									AUDIT_ESCALAMIENTO, occurredAt.toOffsetDateTime())
							.then(recordEvent(candidate.id(), updated, occurredAt))
							.doOnSuccess(row -> log.info("Escalated patient {} ({} min SLA exceeded)",
									candidate.id(), updated.slaMinutes()))
							.thenReturn(true);
				})
				.defaultIfEmpty(false)
				.as(transaction::transactional);
	}

	private Mono<OutboxEvento> recordEvent(UUID patientId, EscalatedPatient updated, ZonedDateTime occurredAt) {
		UUID eventId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();

		PacienteEscalado event = new PacienteEscalado(eventId, SCHEMA_VERSION, patientId, updated.sequence(),
				occurredAt, correlationId.toString(), updated.triageLevel(), updated.slaMinutes(), occurredAt);

		String json;
		try {
			json = objectMapper.writeValueAsString(event);
		}
		catch (JsonProcessingException e) {
			return Mono.error(new IllegalStateException("Cannot serialize " + EVT_02_TYPE, e));
		}
		return template.insert(new OutboxEvento(eventId, EVT_02_TYPE, json, patientId, updated.sequence(),
				occurredAt.toOffsetDateTime(), correlationId));
	}
}
