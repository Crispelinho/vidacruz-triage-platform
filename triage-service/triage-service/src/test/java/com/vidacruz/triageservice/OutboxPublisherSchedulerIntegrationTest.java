package com.vidacruz.triageservice;

import static com.vidacruz.triageservice.OutboxTestSupport.EVT_01;
import static com.vidacruz.triageservice.OutboxTestSupport.EVT_02;
import static com.vidacruz.triageservice.OutboxTestSupport.EVT_03;
import static com.vidacruz.triageservice.OutboxTestSupport.EVT_04;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.kafka.KafkaContainer;

import com.vidacruz.triageservice.OutboxTestSupport.Seeded;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventoRepository;

/**
 * UC-T08 scheduled trigger: the background {@code @Scheduled} task publishes enqueued rows with no explicit call.
 *
 * <p>Separate from {@link OutboxPublisherIntegrationTest} because it needs the scheduler running at a short interval
 * (1 s instead of the documented default), which would make the direct-call tests race with the background task.
 * Nothing in this class calls {@code OutboxPublisher.publish()}: if rows get published, the scheduler did it.
 */
@Import(TriageTestContainers.class)
@SpringBootTest(properties = {
		"app.outbox.publisher-enabled=true",
		"app.outbox.batch-size=50",
		"app.outbox.lease-seconds=30",
		"app.outbox.publish-interval-ms=1000"
})
@DisplayName("UC-T08: outbox publisher scheduled trigger")
class OutboxPublisherSchedulerIntegrationTest {

	@Autowired
	private OutboxEventoRepository repository;

	@Autowired
	private DatabaseClient db;

	@Autowired
	private KafkaContainer kafka;

	@BeforeEach
	void cleanOutbox() {
		OutboxTestSupport.cleanOutbox(db);
	}

	@Test
	@DisplayName("Scheduler: 10 enqueued rows are published within 10 s without any explicit trigger")
	void schedulerPublishesEnqueuedRows() {
		String[] types = { EVT_01, EVT_02, EVT_03, EVT_04 };
		OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
		List<Seeded> seeded = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			seeded.add(OutboxTestSupport.insert(db, types[i % types.length], base.plusSeconds(i)));
		}
		assertThat(repository.countUnpublished().block()).as("enqueued, nothing published yet").isEqualTo(10L);

		await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(250))
				.untilAsserted(() -> assertThat(repository.countUnpublished().block())
						.as("scheduler drained the outbox").isZero());

		assertThat(OutboxTestSupport.countPublished(db, seeded)).as("all 10 rows have publicado_en").isEqualTo(10L);

		List<ConsumerRecord<String, String>> records = OutboxTestSupport.consume(
				kafka.getBootstrapServers(), seeded, 10, Duration.ofSeconds(3));
		assertThat(records).as("10 events on the topic, none duplicated by later scheduler runs").hasSize(10);
		assertThat(records).extracting(ConsumerRecord::key).doesNotHaveDuplicates();
	}
}
