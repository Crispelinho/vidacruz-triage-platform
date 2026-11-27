package com.vidacruz.triageservice.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.vidacruz.triageservice.domain.model.TriageLevel;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MicrometerTriageMetricsTest {

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

	private final MicrometerTriageMetrics metrics = new MicrometerTriageMetrics(registry);

	@Test
	void registersEverySeriesAtZeroForEveryLevel() {
		assertThat(Arrays.stream(TriageLevel.values())
				.map(level -> registry.find(MicrometerTriageMetrics.CLASSIFIED)
						.tag(MicrometerTriageMetrics.LEVEL_TAG, level.name()).counter())
				.allMatch(counter -> counter != null && counter.count() == 0.0d)).isTrue();
	}

	@Test
	void incrementsOnlyTheLevelOfTheRecordedEvent() {
		metrics.patientClassified(TriageLevel.CRITICO);
		metrics.patientEscalated(TriageLevel.URGENTE);
		metrics.alertAcknowledged(TriageLevel.CRITICO);

		assertThat(registry.find(MicrometerTriageMetrics.CLASSIFIED)
				.tag(MicrometerTriageMetrics.LEVEL_TAG, "CRITICO").counter().count()).isEqualTo(1.0d);
		assertThat(registry.find(MicrometerTriageMetrics.CLASSIFIED)
				.tag(MicrometerTriageMetrics.LEVEL_TAG, "NO_URGENTE").counter().count()).isZero();
		assertThat(registry.find(MicrometerTriageMetrics.ESCALATED)
				.tag(MicrometerTriageMetrics.LEVEL_TAG, "URGENTE").counter().count()).isEqualTo(1.0d);
		assertThat(registry.find(MicrometerTriageMetrics.ACKNOWLEDGED)
				.tag(MicrometerTriageMetrics.LEVEL_TAG, "CRITICO").counter().count()).isEqualTo(1.0d);
	}
}
