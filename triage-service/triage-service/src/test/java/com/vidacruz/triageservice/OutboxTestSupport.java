package com.vidacruz.triageservice;

import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Shared seeding / inspection helpers for the UC-T08 outbox publisher integration tests. Package-private on purpose:
 * it only exists to keep {@link OutboxPublisherIntegrationTest} and {@link OutboxPublisherSchedulerIntegrationTest}
 * readable.
 */
final class OutboxTestSupport {

	static final String TOPIC = "triaje.eventos";

	/** Event types exactly as the writers store them in {@code outbox_evento.event_type}. */
	static final String EVT_01 = "paciente_registrado";
	static final String EVT_02 = "paciente_escalado";
	static final String EVT_03 = "paciente_en_consulta";
	static final String EVT_04 = "paciente_alta";

	static final ObjectMapper JSON = new ObjectMapper();

	private OutboxTestSupport() {
	}

	/** An outbox row seeded by a test. Each row gets its own patient, so the Kafka key identifies the row. */
	record Seeded(UUID eventId, UUID pacienteId, String eventType) {
	}

	/** Columns the publisher writes. */
	record OutboxRow(OffsetDateTime publicadoEn, OffsetDateTime leaseUntil, String leaseOwner, int intentos,
			String ultimoError, boolean requiereRevision) {

		boolean published() {
			return publicadoEn != null;
		}
	}

	// ------------------------------------------------------------------ seeding

	/** Inserts a well-formed envelope for {@code eventType}; {@code createdAt} controls the publish order. */
	static Seeded insert(DatabaseClient db, String eventType, OffsetDateTime createdAt) {
		return insert(db, eventType, createdAt, 0);
	}

	/**
	 * Same as {@link #insert(DatabaseClient, String, OffsetDateTime)} with a {@code padding} field of the given length.
	 * Used to build a payload the producer refuses to send (larger than {@code max.request.size}, 1 MiB).
	 */
	static Seeded insert(DatabaseClient db, String eventType, OffsetDateTime createdAt, int paddingChars) {
		UUID eventId = UUID.randomUUID();
		UUID pacienteId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();
		OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);

		Map<String, Object> body = payloadFor(eventType, occurredAt);
		body.put("eventType", eventType);
		body.put("eventId", eventId.toString());
		body.put("schemaVersion", 1);
		body.put("pacienteId", pacienteId.toString());
		body.put("pacienteSequence", 1);
		body.put("occurredAt", occurredAt.toInstant().toString());
		body.put("correlationId", correlationId.toString());
		if (paddingChars > 0) {
			body.put("padding", "x".repeat(paddingChars));
		}

		db.sql("INSERT INTO outbox_evento (event_id, event_type, payload, paciente_id, paciente_sequence, "
				+ "occurred_at, correlation_id, created_at, updated_at) "
				+ "VALUES (:eventId, :eventType, :payload, :pacienteId, 1, :occurredAt, :correlationId, "
				+ ":createdAt, :createdAt)")
				.bind("eventId", eventId)
				.bind("eventType", eventType)
				.bind("payload", serialize(body))
				.bind("pacienteId", pacienteId)
				.bind("occurredAt", occurredAt)
				.bind("correlationId", correlationId)
				.bind("createdAt", createdAt)
				.then()
				.block(Duration.ofSeconds(30));
		return new Seeded(eventId, pacienteId, eventType);
	}

	/** Event specific fields, mirroring the documented EVT-01..EVT-04 payloads. */
	private static Map<String, Object> payloadFor(String eventType, OffsetDateTime at) {
		Map<String, Object> payload = new LinkedHashMap<>();
		String now = at.toInstant().toString();
		switch (eventType) {
			case EVT_01 -> {
				payload.put("nivel", "CRITICO");
				payload.put("slaMinutos", 5);
				payload.put("estado", "REGISTRADO");
				payload.put("fechaRegistro", now);
			}
			case EVT_02 -> {
				payload.put("nivel", "URGENTE");
				payload.put("slaMinutos", 10);
				payload.put("fechaEscalamiento", now);
			}
			case EVT_03 -> {
				payload.put("estadoAnterior", "REGISTRADO");
				payload.put("fechaEntradaConsulta", now);
			}
			case EVT_04 -> {
				payload.put("estadoAnterior", "EN_CONSULTA");
				payload.put("fechaAlta", now);
			}
			default -> throw new IllegalArgumentException("Unknown event type " + eventType);
		}
		return payload;
	}

	private static String serialize(Map<String, Object> body) {
		try {
			return JSON.writeValueAsString(body);
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

	// ------------------------------------------------------------------ database inspection

	static void cleanOutbox(DatabaseClient db) {
		db.sql("TRUNCATE TABLE outbox_evento").then().block(Duration.ofSeconds(30));
	}

	static OutboxRow row(DatabaseClient db, UUID eventId) {
		return db.sql("SELECT publicado_en, lease_until, lease_owner, intentos, ultimo_error, requiere_revision "
				+ "FROM outbox_evento WHERE event_id = :id")
				.bind("id", eventId)
				.map((r, m) -> new OutboxRow(
						r.get("publicado_en", OffsetDateTime.class),
						r.get("lease_until", OffsetDateTime.class),
						r.get("lease_owner", String.class),
						r.get("intentos", Integer.class),
						r.get("ultimo_error", String.class),
						Boolean.TRUE.equals(r.get("requiere_revision", Boolean.class))))
				.one()
				.block(Duration.ofSeconds(30));
	}

	static long countPublished(DatabaseClient db, List<Seeded> seeded) {
		return seeded.stream().filter(s -> row(db, s.eventId()).published()).count();
	}

	// ------------------------------------------------------------------ Kafka inspection

	/**
	 * Reads {@value #TOPIC} from the beginning until {@code expected} records keyed by one of the seeded patients are
	 * seen, then keeps listening for {@code settle} to expose duplicates. The topic is shared by every test (it is never
	 * cleaned), so records are filtered by key.
	 */
	static List<ConsumerRecord<String, String>> consume(String bootstrapServers, List<Seeded> seeded, int expected,
			Duration settle) {
		Set<String> keys = new java.util.HashSet<>();
		seeded.forEach(s -> keys.add(s.pacienteId().toString()));

		List<ConsumerRecord<String, String>> matches = new ArrayList<>();
		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerConfig(bootstrapServers))) {
			consumer.subscribe(List.of(TOPIC));

			if (expected > 0) {
				await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200)).until(() -> {
					collect(consumer, keys, matches);
					return matches.size() >= expected;
				});
			}

			long settleUntil = System.nanoTime() + settle.toNanos();
			while (System.nanoTime() < settleUntil) {
				collect(consumer, keys, matches);
			}
		}
		return matches;
	}

	private static void collect(KafkaConsumer<String, String> consumer, Set<String> keys,
			List<ConsumerRecord<String, String>> matches) {
		for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
			if (keys.contains(record.key())) {
				matches.add(record);
			}
		}
	}

	/** Sum of the end offsets of every partition of {@value #TOPIC}; 0 while the topic does not exist yet. */
	static long totalMessagesOnTopic(String bootstrapServers) {
		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerConfig(bootstrapServers))) {
			List<PartitionInfo> partitions = consumer.partitionsFor(TOPIC, Duration.ofSeconds(10));
			if (partitions == null || partitions.isEmpty()) {
				return 0;
			}
			List<TopicPartition> topicPartitions = partitions.stream()
					.map(p -> new TopicPartition(p.topic(), p.partition()))
					.toList();
			return consumer.endOffsets(topicPartitions).values().stream().mapToLong(Long::longValue).sum();
		}
	}

	private static Map<String, Object> consumerConfig(String bootstrapServers) {
		return Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
				ConsumerConfig.GROUP_ID_CONFIG, "uc-t08-it-" + UUID.randomUUID(),
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
				ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
	}
}
