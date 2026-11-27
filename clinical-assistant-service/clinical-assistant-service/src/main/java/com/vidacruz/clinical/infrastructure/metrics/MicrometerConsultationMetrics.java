package com.vidacruz.clinical.infrastructure.metrics;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.vidacruz.clinical.domain.ports.ConsultationMetrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** {@code assistant.answers{outcome}} counters, all pre-registered at zero so a quiet outcome reads 0, not "no data". */
@Component
public class MicrometerConsultationMetrics implements ConsultationMetrics {

	static final String ANSWERS = "assistant.answers";
	static final String OUTCOME_TAG = "outcome";
	static final String ANSWERED = "answered";
	static final String NO_EVIDENCE = "no_evidence";
	static final String INVALID_CITATION = "invalid_citation";
	static final String UNAVAILABLE = "unavailable";

	private final Map<String, Counter> counters;

	public MicrometerConsultationMetrics(MeterRegistry registry) {
		counters = Map.of(ANSWERED, counter(registry, ANSWERED), NO_EVIDENCE, counter(registry, NO_EVIDENCE),
				INVALID_CITATION, counter(registry, INVALID_CITATION), UNAVAILABLE, counter(registry, UNAVAILABLE));
	}

	@Override
	public void answered() {
		counters.get(ANSWERED).increment();
	}

	@Override
	public void noEvidence() {
		counters.get(NO_EVIDENCE).increment();
	}

	@Override
	public void invalidCitation() {
		counters.get(INVALID_CITATION).increment();
	}

	@Override
	public void unavailable() {
		counters.get(UNAVAILABLE).increment();
	}

	private static Counter counter(MeterRegistry registry, String outcome) {
		return Counter.builder(ANSWERS)
				.description("Clinical questions answered, by how the answer was reached")
				.tag(OUTCOME_TAG, outcome)
				.register(registry);
	}
}
