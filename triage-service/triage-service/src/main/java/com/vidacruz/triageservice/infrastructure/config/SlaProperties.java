package com.vidacruz.triageservice.infrastructure.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.vidacruz.triageservice.domain.model.TriageLevel;

/** Configurable RN-02 SLA values. Missing levels fall back to the specification defaults. */
@ConfigurationProperties(prefix = "app.sla")
public record SlaProperties(Map<String, Integer> defaults) {

	public int minutesFor(TriageLevel level) {
		Integer configured = defaults == null ? null : defaults.get(level.name());
		return configured == null ? level.defaultSlaMinutes() : configured;
	}
}
