package com.vidacruz.triageservice.domain.model;

/**
 * Business events of the triage, recorded only after the write that caused them commits (NFR-07).
 * Every method takes a triage level as its only label: no patient id or clinical free text reaches metrics.
 */
public interface TriageMetrics {

	/** A patient was registered and classified with this level (RF-01). */
	void patientClassified(TriageLevel level);

	/** The SLA of a patient of this level passed and the escalation was applied (RF-03). */
	void patientEscalated(TriageLevel level);

	/** A team member took the alert of a patient of this level (RF-06). */
	void alertAcknowledged(TriageLevel level);
}
