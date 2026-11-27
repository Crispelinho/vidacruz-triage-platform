package com.vidacruz.clinical.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MicrometerConsultationMetricsTest {

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

	private final MicrometerConsultationMetrics metrics = new MicrometerConsultationMetrics(registry);

	@Test
	void registersEveryOutcomeAtZero() {
		assertThat(registry.get(MicrometerConsultationMetrics.ANSWERS).meters()).hasSize(4);
		assertThat(registry.get(MicrometerConsultationMetrics.ANSWERS).meters())
				.allMatch(meter -> ((io.micrometer.core.instrument.Counter) meter).count() == 0.0d);
	}

	@Test
	void incrementsOnlyTheRecordedOutcome() {
		metrics.answered();
		metrics.noEvidence();
		metrics.invalidCitation();
		metrics.unavailable();

		assertThat(answers(MicrometerConsultationMetrics.ANSWERED)).isEqualTo(1.0d);
		assertThat(answers(MicrometerConsultationMetrics.NO_EVIDENCE)).isEqualTo(1.0d);
		assertThat(answers(MicrometerConsultationMetrics.INVALID_CITATION)).isEqualTo(1.0d);
		assertThat(answers(MicrometerConsultationMetrics.UNAVAILABLE)).isEqualTo(1.0d);
	}

	private double answers(String outcome) {
		return registry.get(MicrometerConsultationMetrics.ANSWERS)
				.tag(MicrometerConsultationMetrics.OUTCOME_TAG, outcome).counter().count();
	}
}
