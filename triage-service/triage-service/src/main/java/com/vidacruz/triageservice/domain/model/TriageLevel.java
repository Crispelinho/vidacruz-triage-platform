package com.vidacruz.triageservice.domain.model;

/**
 * Triage levels in decreasing severity, as defined by RN-01. The SLA values here are the specification defaults;
 * the effective values are read from configuration.
 */
public enum TriageLevel {
	CRITICO(5),
	URGENTE(10),
	URGENTE_2(30),
	NO_URGENTE(60),
	NO_URGENTE_2(120);

	private final int defaultSlaMinutes;

	TriageLevel(int defaultSlaMinutes) {
		this.defaultSlaMinutes = defaultSlaMinutes;
	}

	public int defaultSlaMinutes() {
		return defaultSlaMinutes;
	}
}
