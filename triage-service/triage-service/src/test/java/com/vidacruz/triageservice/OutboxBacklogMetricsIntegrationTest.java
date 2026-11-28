package com.vidacruz.triageservice;

import static com.vidacruz.triageservice.OutboxTestSupport.EVT_01;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;

import com.vidacruz.triageservice.event.infrastructure.OutboxBacklogMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.test.StepVerifier;

/** The outbox backlog an operator needs to alert on, read from the real table (NFR-07). */
@Import(TriageTestContainers.class)
@SpringBootTest(properties = {
		"app.outbox.publisher-enabled=false",
		"app.outbox.metrics-interval-ms=86400000"
})
class OutboxBacklogMetricsIntegrationTest {

	@Autowired
	private OutboxBacklogMetrics metrics;

	@Autowired
	private MeterRegistry registry;

	@Autowired
	private DatabaseClient db;

	@BeforeEach
	void cleanOutbox() {
		OutboxTestSupport.cleanOutbox(db);
	}

	@Test
	void readsZeroForAnEmptyOutbox() {
		StepVerifier.create(metrics.refresh()).expectComplete().verify(Duration.ofSeconds(30));

		assertThat(gauge("triage.outbox.pending")).isZero();
		assertThat(gauge("triage.outbox.failing")).isZero();
		assertThat(gauge("triage.outbox.requires.review")).isZero();
		assertThat(gauge("triage.outbox.oldest.pending.age")).isZero();
	}

	@Test
	void countsUnpublishedRowsAndTheAgeOfTheOldestOne() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		UUID oldest = OutboxTestSupport.insert(db, EVT_01, now.minusMinutes(10)).eventId();
		UUID flagged = OutboxTestSupport.insert(db, EVT_01, now.minusMinutes(2)).eventId();
		OutboxTestSupport.insert(db, EVT_01, now.minusMinutes(1));
		UUID published = OutboxTestSupport.insert(db, EVT_01, now.minusHours(5)).eventId();
		update("SET intentos = 3 WHERE event_id = :id", oldest);
		update("SET intentos = 10, requiere_revision = TRUE WHERE event_id = :id", flagged);
		update("SET publicado_en = CURRENT_TIMESTAMP, intentos = 1 WHERE event_id = :id", published);

		StepVerifier.create(metrics.refresh()).expectComplete().verify(Duration.ofSeconds(30));

		assertThat(gauge("triage.outbox.pending")).isEqualTo(3);
		assertThat(gauge("triage.outbox.failing")).isEqualTo(2);
		assertThat(gauge("triage.outbox.requires.review")).isEqualTo(1);
		assertThat(gauge("triage.outbox.oldest.pending.age")).isCloseTo(600, within(60.0));
	}

	private void update(String assignment, UUID eventId) {
		db.sql("UPDATE outbox_evento " + assignment).bind("id", eventId).then().block(Duration.ofSeconds(30));
	}

	private double gauge(String name) {
		return registry.get(name).gauge().value();
	}
}
