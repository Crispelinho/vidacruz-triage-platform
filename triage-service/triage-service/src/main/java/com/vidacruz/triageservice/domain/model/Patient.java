package com.vidacruz.triageservice.domain.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Persisted state of a triaged patient (RF-01/RF-02). Clinical vitals keep the nullable shape of the record. */
public record Patient(
		UUID id,
		String fullName,
		Integer age,
		Integer heartRate,
		Integer systolic,
		Integer diastolic,
		Integer spo2,
		BigDecimal temperature,
		Integer pain,
		Integer gcs,
		String complaint,
		TriageLevel level,
		int slaMinutes,
		String status,
		boolean escalated,
		Instant registeredAt,
		Instant lastChangedAt,
		Instant statusSince) {

	/** API-03 view: the timer follows the status start, not the last mutation, so escalation keeps the status. */
	public PatientDetail detailAt(Instant now) {
		return detail(status, statusSince, now);
	}

	/** API-02 view right after a transition: the new status has just started. */
	public PatientDetail detailAfterTransition(String newStatus, Instant changedAt) {
		return detail(newStatus, changedAt, changedAt);
	}

	private PatientDetail detail(String currentStatus, Instant statusStart, Instant now) {
		long minutes = Duration.between(statusStart, now).toMinutes();
		return new PatientDetail(id, fullName, level, slaMinutes, currentStatus, escalated, registeredAt, minutes, age,
				heartRate, systolic, diastolic, spo2, temperature, pain, gcs, complaint);
	}
}
