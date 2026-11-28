package com.vidacruz.triageservice.domain.model;

/** RN-02: SLA minutes for each triage level. */
public interface SlaPolicy {

	int minutesFor(TriageLevel level);
}
