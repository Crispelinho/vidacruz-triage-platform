package com.vidacruz.triageservice.infrastructure.metrics;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.model.TriageMetrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Micrometer counters for the triage business events (NFR-07). One series per triage level, pre-registered with zero
 * so a rate over a level with no patients reads 0 instead of "no data". Prometheus exposes them with the
 * {@code _total} suffix, for example {@code triage_patients_classified_total{level="CRITICO"}}.
 */
@Component
public class MicrometerTriageMetrics implements TriageMetrics {

	static final String CLASSIFIED = "triage.patients.classified";
	static final String ESCALATED = "triage.patients.escalated";
	static final String ACKNOWLEDGED = "triage.alerts.acknowledged";
	static final String LEVEL_TAG = "level";

	private final Map<TriageLevel, Counter> classified;
	private final Map<TriageLevel, Counter> escalated;
	private final Map<TriageLevel, Counter> acknowledged;

	public MicrometerTriageMetrics(MeterRegistry registry) {
		classified = countersByLevel(registry, CLASSIFIED,
				"Patients registered and classified, by triage level");
		escalated = countersByLevel(registry, ESCALATED,
				"Patients escalated because their SLA passed, by triage level");
		acknowledged = countersByLevel(registry, ACKNOWLEDGED,
				"Alerts taken by the medical team, by triage level");
	}

	@Override
	public void patientClassified(TriageLevel level) {
		classified.get(level).increment();
	}

	@Override
	public void patientEscalated(TriageLevel level) {
		escalated.get(level).increment();
	}

	@Override
	public void alertAcknowledged(TriageLevel level) {
		acknowledged.get(level).increment();
	}

	private static Map<TriageLevel, Counter> countersByLevel(MeterRegistry registry, String name,
			String description) {
		Map<TriageLevel, Counter> counters = new EnumMap<>(TriageLevel.class);
		for (TriageLevel level : TriageLevel.values()) {
			counters.put(level, Counter.builder(name)
					.description(description)
					.tag(LEVEL_TAG, level.name())
					.register(registry));
		}
		return counters;
	}
}
