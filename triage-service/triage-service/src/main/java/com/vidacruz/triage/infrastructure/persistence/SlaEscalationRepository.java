package com.vidacruz.triage.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** SQL for the RF-03 SLA evaluator. Every method joins the caller's reactive transaction. */
@Repository
public class SlaEscalationRepository {

	/** An overdue patient found by the read-only candidate scan. */
	public record Candidate(UUID id, String triageLevel, int slaMinutes, LocalDateTime registeredAt) {
	}

	/** Columns the escalation needs back from the guarded UPDATE. */
	public record EscalatedPatient(String triageLevel, int slaMinutes, LocalDateTime registeredAt, int sequence) {
	}

	private final DatabaseClient db;

	public SlaEscalationRepository(DatabaseClient db) {
		this.db = db;
	}

	/**
	 * REGISTRADO, not yet escalated patients whose elapsed time is strictly greater than the SLA stored at
	 * registration (AMB-04: elapsed == SLA is not overdue). Only a hint: the guarded update in
	 * {@link #escalate} decides the winner.
	 */
	public Flux<Candidate> findOverdue(LocalDateTime now) {
		return db.sql("""
				SELECT id, triage_level, sla_minutes, registered_at
				FROM patients
				WHERE state = 'REGISTRADO' AND escalated = false
				  AND registered_at + sla_minutes * INTERVAL '1 minute' < :now
				ORDER BY registered_at
				""")
				.bind("now", now)
				.map((row, md) -> new Candidate(
						row.get("id", UUID.class),
						row.get("triage_level", String.class),
						row.get("sla_minutes", Integer.class),
						row.get("registered_at", LocalDateTime.class)))
				.all();
	}

	/**
	 * RN-03 idempotence guard (triage-service.md section 7.2). The WHERE clause is the whole contract: the
	 * row changes only while the patient is still REGISTRADO and not escalated, so a concurrent evaluator or a
	 * concurrent transition to EN_CONSULTA/ALTA leaves it untouched. Empty when no row matched. Takes the
	 * next per-patient event sequence in the same statement; the state itself does not change.
	 */
	public Mono<EscalatedPatient> escalate(UUID id, LocalDateTime now) {
		return db.sql("""
				UPDATE patients
				SET escalated = true, updated_at = :now, last_sequence = last_sequence + 1
				WHERE id = :id AND state = 'REGISTRADO' AND escalated = false
				RETURNING triage_level, sla_minutes, registered_at, last_sequence
				""")
				.bind("now", now)
				.bind("id", id)
				.map((row, md) -> new EscalatedPatient(
						row.get("triage_level", String.class),
						row.get("sla_minutes", Integer.class),
						row.get("registered_at", LocalDateTime.class),
						row.get("last_sequence", Integer.class)))
				.one();
	}
}
