package com.vidacruz.triageservice;

import static com.vidacruz.triageservice.OutboxTestSupport.EVT_01;
import static com.vidacruz.triageservice.OutboxTestSupport.EVT_02;
import static com.vidacruz.triageservice.OutboxTestSupport.EVT_03;
import static com.vidacruz.triageservice.OutboxTestSupport.EVT_04;
import static com.vidacruz.triageservice.OutboxTestSupport.JSON;
import static com.vidacruz.triageservice.OutboxTestSupport.TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.kafka.KafkaContainer;

import com.vidacruz.triageservice.OutboxTestSupport.OutboxRow;
import com.vidacruz.triageservice.OutboxTestSupport.Seeded;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventoRepository;
import com.vidacruz.triageservice.event.infrastructure.OutboxPublisher;

/**
 * UC-T08 integration tests: the outbox relay (rows in {@code outbox_evento} to topic {@code triaje.eventos}).
 *
 * <p>The publisher is invoked directly ({@link OutboxPublisher#publish()}), never through the scheduler. The
 * {@code @Scheduled} trigger is parked with a 24 h interval so it cannot steal rows from a running test; the
 * scheduled path has its own class, {@link OutboxPublisherSchedulerIntegrationTest}, because it needs a short interval
 * and a context of its own.
 *
 * <p>Where the ticket wording differs from the code under test, this class follows the code and the schema:
 * <ul>
 *   <li>The publication column is {@code publicado_en} (not {@code published_at}).</li>
 *   <li>The Kafka record key is the <b>patient id</b> (per-patient ordering), not the event id. The event id travels in
 *       the payload envelope, and is asserted there.</li>
 *   <li>There is no HTTP trigger: "200 / count=0" becomes {@code publish()} emitting {@code 0}.</li>
 *   <li>{@code outbox_evento} has no offset or partition column. The Kafka record metadata (partition, offset) is
 *       verified on the consumed record instead.</li>
 *   <li>The publisher relays {@code payload} as an opaque string, so invalid JSON is NOT a failure. The poison row used
 *       for the partial-failure case is a payload larger than the producer's {@code max.request.size} (1 MiB), which
 *       the producer rejects for that record only.</li>
 *   <li>Event types are stored as the writers emit them: {@code paciente_registrado}, {@code paciente_escalado},
 *       {@code paciente_en_consulta}, {@code paciente_alta}.</li>
 * </ul>
 *
 * <p>The tests only exercise {@code OutboxPublisher}; run them once the publisher properties
 * ({@code app.outbox.batch-size}, {@code lease-seconds}, {@code publish-interval-ms}) are set below.
 */
@Import(TriageTestContainers.class)
@SpringBootTest(properties = {
		"app.outbox.publisher-enabled=true",
		"app.outbox.batch-size=50",
		"app.outbox.lease-seconds=30",
		"app.outbox.publish-interval-ms=PT24H"
})
@DisplayName("UC-T08: outbox publisher (outbox_evento -> triaje.eventos)")
class OutboxPublisherIntegrationTest {

	private static final Duration SETTLE = Duration.ofSeconds(3);

	@Autowired
	private OutboxPublisher publisher;

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

	// ------------------------------------------------------------------ helpers

	/** Seeds {@code count} rows cycling through EVT-01..EVT-04, oldest first. */
	private List<Seeded> seedMixed(int count) {
		String[] types = { EVT_01, EVT_02, EVT_03, EVT_04 };
		OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);
		List<Seeded> seeded = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			seeded.add(OutboxTestSupport.insert(db, types[i % types.length], base.plusSeconds(i)));
		}
		return seeded;
	}

	private int runPublisher() {
		Integer published = publisher.publish().block(Duration.ofSeconds(60));
		assertThat(published).isNotNull();
		return published;
	}

	private List<ConsumerRecord<String, String>> consume(List<Seeded> seeded, int expected) {
		return OutboxTestSupport.consume(kafka.getBootstrapServers(), seeded, expected, SETTLE);
	}

	// ------------------------------------------------------------------ 1. happy path

	@Test
	@DisplayName("Happy path: 5 unpublished rows of mixed types are all marked published and sent to the topic")
	void publishesEveryUnpublishedRow() throws Exception {
		List<Seeded> seeded = seedMixed(5);
		assertThat(seeded).extracting(Seeded::eventType).containsExactly(EVT_01, EVT_02, EVT_03, EVT_04, EVT_01);
		assertThat(repository.countUnpublished().block()).isEqualTo(5L);

		int published = runPublisher();

		assertThat(published).as("rows published by this run").isEqualTo(5);
		assertThat(repository.countUnpublished().block()).as("nothing left to publish").isZero();
		for (Seeded s : seeded) {
			OutboxRow row = OutboxTestSupport.row(db, s.eventId());
			assertThat(row.published()).as("publicado_en of %s", s.eventType()).isTrue();
			assertThat(row.leaseUntil()).as("lease released once published").isNull();
			assertThat(row.leaseOwner()).isNull();
		}

		List<ConsumerRecord<String, String>> records = consume(seeded, 5);
		assertThat(records).as("exactly 5 events for the seeded patients on %s", TOPIC).hasSize(5);
		List<String> seenEventIds = new ArrayList<>();
		for (ConsumerRecord<String, String> record : records) {
			JsonNode event = JSON.readTree(record.value());
			seenEventIds.add(event.path("eventId").asText());
			Seeded origin = seeded.stream()
					.filter(s -> s.eventId().toString().equals(event.path("eventId").asText()))
					.findFirst()
					.orElseThrow(() -> new AssertionError("unexpected event on the topic: " + record.value()));
			assertThat(record.key()).as("key is the patient id (per-patient ordering)")
					.isEqualTo(origin.pacienteId().toString());
			assertThat(event.path("pacienteId").asText()).isEqualTo(record.key());
			assertThat(event.path("eventType").asText()).isEqualTo(origin.eventType());
		}
		assertThat(seenEventIds)
				.as("every event id appears once")
				.containsExactlyInAnyOrderElementsOf(seeded.stream().map(s -> s.eventId().toString()).toList());
	}

	// ------------------------------------------------------------------ 2. idempotency

	@Test
	@DisplayName("Idempotency: a second run publishes nothing, sends nothing and leaves publicado_en untouched")
	void secondRunDoesNothing() {
		List<Seeded> seeded = seedMixed(5);
		assertThat(runPublisher()).isEqualTo(5);
		assertThat(consume(seeded, 5)).hasSize(5);

		Map<Seeded, OffsetDateTime> publishedAt = new LinkedHashMap<>();
		for (Seeded s : seeded) {
			publishedAt.put(s, OutboxTestSupport.row(db, s.eventId()).publicadoEn());
		}
		long topicSize = OutboxTestSupport.totalMessagesOnTopic(kafka.getBootstrapServers());

		int second = runPublisher();

		assertThat(second).as("second run: count = 0").isZero();
		publishedAt.forEach((s, before) -> assertThat(OutboxTestSupport.row(db, s.eventId()).publicadoEn())
				.as("publicado_en of %s unchanged", s.eventType())
				.isEqualTo(before));
		assertThat(OutboxTestSupport.totalMessagesOnTopic(kafka.getBootstrapServers()))
				.as("no new record on the topic").isEqualTo(topicSize);
		assertThat(consume(seeded, 5)).as("still exactly one event per row").hasSize(5);
	}

	// ------------------------------------------------------------------ 3. partial failure

	@Test
	@DisplayName("Partial failure: a row the producer rejects is skipped and recorded; the other 3 are published")
	void badRowIsSkippedAndTheRestIsPublished() {
		OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);
		Seeded good1 = OutboxTestSupport.insert(db, EVT_01, base);
		// 1.2 MB payload > producer max.request.size (1 MiB): RecordTooLargeException for this record only.
		// It sits in the middle of the batch so the rows after it prove the run did not abort.
		Seeded poison = OutboxTestSupport.insert(db, EVT_02, base.plusSeconds(1), 1_200_000);
		Seeded good2 = OutboxTestSupport.insert(db, EVT_03, base.plusSeconds(2));
		Seeded good3 = OutboxTestSupport.insert(db, EVT_04, base.plusSeconds(3));

		int published = runPublisher();

		assertThat(published).as("only the 3 good rows count").isEqualTo(3);
		for (Seeded good : List.of(good1, good2, good3)) {
			assertThat(OutboxTestSupport.row(db, good.eventId()).published()).as("good row %s", good.eventType())
					.isTrue();
		}

		OutboxRow bad = OutboxTestSupport.row(db, poison.eventId());
		assertThat(bad.published()).as("bad row is NOT marked published").isFalse();
		assertThat(bad.intentos()).as("the failure is counted").isEqualTo(1);
		assertThat(bad.ultimoError()).as("the failure reason is recorded").isNotBlank();
		assertThat(bad.requiereRevision()).as("a single failure does not need manual review").isFalse();
		assertThat(repository.countUnpublished().block()).as("bad row stays in the outbox").isEqualTo(1L);

		List<Seeded> all = List.of(good1, poison, good2, good3);
		List<ConsumerRecord<String, String>> records = consume(all, 3);
		assertThat(records).as("3 events on the topic, the bad one was never sent").hasSize(3);
		assertThat(records).extracting(ConsumerRecord::key)
				.containsExactlyInAnyOrder(good1.pacienteId().toString(), good2.pacienteId().toString(),
						good3.pacienteId().toString())
				.doesNotContain(poison.pacienteId().toString());
	}

	// ------------------------------------------------------------------ 5. mixed event types

	@Test
	@DisplayName("Mixed types: EVT-01..EVT-04 in one batch each reach the topic with their own payload fields")
	void everyEventTypeIsPublishedWithItsPayload() throws Exception {
		OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);
		Seeded registrado = OutboxTestSupport.insert(db, EVT_01, base);
		Seeded escalado = OutboxTestSupport.insert(db, EVT_02, base.plusSeconds(1));
		Seeded enConsulta = OutboxTestSupport.insert(db, EVT_03, base.plusSeconds(2));
		Seeded alta = OutboxTestSupport.insert(db, EVT_04, base.plusSeconds(3));
		List<Seeded> all = List.of(registrado, escalado, enConsulta, alta);

		assertThat(runPublisher()).isEqualTo(4);

		Map<String, JsonNode> byPatient = new LinkedHashMap<>();
		for (ConsumerRecord<String, String> record : consume(all, 4)) {
			byPatient.put(record.key(), JSON.readTree(record.value()));
		}
		assertThat(byPatient).as("one event per type, no duplicates").hasSize(4);

		JsonNode evt01 = byPatient.get(registrado.pacienteId().toString());
		assertThat(evt01.path("eventType").asText()).isEqualTo(EVT_01);
		assertThat(evt01.path("nivel").asText()).isEqualTo("CRITICO");
		assertThat(evt01.path("slaMinutos").asInt()).isEqualTo(5);
		assertThat(evt01.path("estado").asText()).isEqualTo("REGISTRADO");
		assertThat(evt01.hasNonNull("fechaRegistro")).isTrue();

		JsonNode evt02 = byPatient.get(escalado.pacienteId().toString());
		assertThat(evt02.path("eventType").asText()).isEqualTo(EVT_02);
		assertThat(evt02.path("nivel").asText()).isEqualTo("URGENTE");
		assertThat(evt02.path("slaMinutos").asInt()).isEqualTo(10);
		assertThat(evt02.hasNonNull("fechaEscalamiento")).isTrue();

		JsonNode evt03 = byPatient.get(enConsulta.pacienteId().toString());
		assertThat(evt03.path("eventType").asText()).isEqualTo(EVT_03);
		assertThat(evt03.path("estadoAnterior").asText()).isEqualTo("REGISTRADO");
		assertThat(evt03.hasNonNull("fechaEntradaConsulta")).isTrue();

		JsonNode evt04 = byPatient.get(alta.pacienteId().toString());
		assertThat(evt04.path("eventType").asText()).isEqualTo(EVT_04);
		assertThat(evt04.path("estadoAnterior").asText()).isEqualTo("EN_CONSULTA");
		assertThat(evt04.hasNonNull("fechaAlta")).isTrue();

		for (Map.Entry<String, JsonNode> entry : byPatient.entrySet()) {
			JsonNode event = entry.getValue();
			assertThat(event.path("pacienteId").asText()).isEqualTo(entry.getKey());
			assertThat(event.path("schemaVersion").asInt()).isEqualTo(1);
			assertThat(event.path("pacienteSequence").asInt()).isEqualTo(1);
			assertThat(event.hasNonNull("eventId")).isTrue();
			assertThat(event.hasNonNull("occurredAt")).isTrue();
			assertThat(event.hasNonNull("correlationId")).isTrue();
		}
	}

	// ------------------------------------------------------------------ 6. empty outbox

	@Test
	@DisplayName("Empty outbox: publish() emits 0 without errors and nothing is sent to Kafka")
	void emptyOutboxIsANoOp() {
		assertThat(repository.countUnpublished().block()).isZero();
		long before = OutboxTestSupport.totalMessagesOnTopic(kafka.getBootstrapServers());

		int published = runPublisher();

		assertThat(published).as("count = 0").isZero();
		assertThat(OutboxTestSupport.totalMessagesOnTopic(kafka.getBootstrapServers()))
				.as("no Kafka message").isEqualTo(before);
		assertThat(repository.countUnpublished().block()).isZero();
	}

	// ------------------------------------------------------------------ 7. offset tracking

	@Test
	@DisplayName("Record metadata: a published row lands on a real partition and offset of triaje.eventos")
	void publishedRowHasKafkaPartitionAndOffset() {
		Seeded seeded = OutboxTestSupport.insert(db, EVT_01, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));

		assertThat(runPublisher()).isEqualTo(1);

		OutboxRow row = OutboxTestSupport.row(db, seeded.eventId());
		assertThat(row.published()).isTrue();
		assertThat(row.leaseUntil()).isNull();

		// outbox_evento stores no offset/partition (schema V1): the broker-side coordinates are the proof of delivery.
		List<ConsumerRecord<String, String>> records = consume(List.of(seeded), 1);
		assertThat(records).singleElement().satisfies(record -> {
			assertThat(record.topic()).isEqualTo(TOPIC);
			assertThat(record.partition()).as("partition assigned by the broker").isGreaterThanOrEqualTo(0);
			assertThat(record.offset()).as("offset assigned by the broker").isGreaterThanOrEqualTo(0L);
			assertThat(record.key()).isEqualTo(seeded.pacienteId().toString());
		});
	}
}
