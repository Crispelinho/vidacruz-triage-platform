package com.vidacruz.triageservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.kafka.KafkaContainer;
import reactor.core.publisher.Mono;

/**
 * RF-03 integration tests: SLA evaluation and escalation (RN-03, DEC-04, ADR-13, UC-T05).
 *
 * <p>Scope: an overdue {@code REGISTRADO} patient is escalated exactly once (flag, {@code ESCALAMIENTO} audit row
 * and EVT-02 outbox row, all in one transaction), a second evaluation changes nothing, patients that left
 * {@code REGISTRADO} are never escalated, and concurrent evaluators ("two replicas") produce one winner per patient.
 * Alert dispatching (RF-06), the Redis projection (RF-04-B) and the state machine itself (RF-02) are out of scope.
 *
 * <p>Contract source: docs/requirements.md RF-03 / RN-03 / AMB-04, docs/adr/ADR-13-sla-evaluation.md and
 * docs/services/triage-service.md section 2.3 and 7.4. Where this class differs from the ticket wording, it follows
 * those documents:
 * <ul>
 *   <li>The escalation event is <b>EVT-02</b> ({@code paciente_escalado}, {@code EVT_02_PACIENTE_ESCALADO}). The ticket
 *       called it EVT-05, which does not exist: {@code EventType} only declares EVT-01..EVT-04.</li>
 *   <li>Overdue means elapsed time <b>strictly greater</b> than the stored SLA (AMB-04). Equal is not overdue, and a
 *       dedicated test pins that boundary.</li>
 *   <li>The ticket's "WHERE escalated=false passes" for concurrency is the documented conditional update
 *       {@code WHERE id = ? AND escalado = false AND estado = 'REGISTRADO'}: the row-changing evaluator alone writes
 *       audit and EVT-02.</li>
 * </ul>
 *
 * <p>Assumptions on code that does not exist yet (the scheduler change is still pending). They are all concentrated
 * in the constants below and in {@link #runEvaluator()}, so adapting this class is a one-place edit:
 * <ul>
 *   <li>{@link #EVALUATOR_CLASS} is a Spring bean exposing a no-argument method named {@link #EVALUATOR_METHOD}
 *       that runs ONE evaluation pass over every overdue patient. It may return {@code void}, a value, or a
 *       {@code Mono}/{@code Publisher} (which is subscribed and awaited). It is looked up reflectively so this class
 *       compiles before the evaluator exists.</li>
 *   <li>The evaluator takes "now" from the injected {@link Clock} bean (as RF-01 and RF-02 do), so a controllable clock
 *       replaces sleeping. A failed pass must not throw for patients lost to another evaluator.</li>
 *   <li>The background {@code @Scheduled} trigger is parked with {@code triage.sla.evaluation-interval} (the
 *       documented property, NFR-08) so that only the explicit {@link #runEvaluator()} calls evaluate. If the property
 *       ends up with another name or type, adjust {@link #BACKGROUND_INTERVAL_PROPERTY}.</li>
 *   <li>Escalation does not change {@code state} (the patient stays REGISTRADO) and bumps {@code last_sequence}, so the
 *       EVT-02 row takes {@code paciente_sequence = 2} right after registration (triage-service.md 7.6).</li>
 * </ul>
 */
@Import({TriageTestContainers.class, TriageSLAEscalationIntegrationTest.ClockConfig.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { TriageSLAEscalationIntegrationTest.BACKGROUND_INTERVAL_PROPERTY,
				"spring.main.allow-bean-definition-overriding=true" })
@AutoConfigureWebTestClient
@DisplayName("RF-03: SLA evaluation and escalation (RN-03 idempotence and concurrency)")
class TriageSLAEscalationIntegrationTest {

	/** Parks the periodic trigger (24 h) so the test is the only caller of the evaluator. */
	static final String BACKGROUND_INTERVAL_PROPERTY = "triage.sla.evaluation-interval=PT24H";

	/** Bean that runs one evaluation pass. ADAPT HERE when the scheduler change lands. */
	private static final String EVALUATOR_CLASS = "com.vidacruz.triageservice.domain.service.SlaEvaluator";
	private static final String EVALUATOR_METHOD = "evaluateOnce";

	private static final String COLLECTION = "/triage/pacientes";
	private static final String TOPIC = "triaje.eventos";

	private static final String REGISTRADO = "REGISTRADO";
	private static final String EN_CONSULTA = "EN_CONSULTA";
	private static final String ALTA = "ALTA";
	private static final String ESCALAMIENTO = "ESCALAMIENTO";

	/** Envelope names: the spec says snake_case, the event model serializes the EVT-prefixed name. */
	private static final Set<String> EVT_01_TYPES = Set.of("EVT_01_PACIENTE_REGISTRADO", "paciente_registrado");
	private static final Set<String> EVT_02_TYPES = Set.of("EVT_02_PACIENTE_ESCALADO", "paciente_escalado");

	private static final ObjectMapper JSON = new ObjectMapper();

	/** Triage levels used by the tests, with the SLA the registration must have stored (RN-02). */
	private enum Level {
		CRITICO(5), URGENTE(10), URGENTE_2(30), NO_URGENTE(60), NO_URGENTE_2(120);

		final int slaMinutes;

		Level(int slaMinutes) {
			this.slaMinutes = slaMinutes;
		}
	}

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private DatabaseClient databaseClient;

	@Autowired
	private KafkaContainer kafka;

	@Autowired
	private ApplicationContext context;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void cleanStateAndResetClock() {
		// Isolation: counts such as "exactly 5 events" must not see patients from other tests of this class.
		execute("TRUNCATE TABLE auditoria_clinica, outbox_evento, paciente CASCADE");
		clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
	}

	// ------------------------------------------------------------------ controllable time (same approach as RF-02)

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock clock() {
			return new MutableClock(Instant.now().truncatedTo(ChronoUnit.MICROS));
		}
	}

	/** UTC clock whose instant only moves when a test says so. */
	static final class MutableClock extends Clock {

		private final AtomicReference<Instant> now;

		MutableClock(Instant start) {
			this.now = new AtomicReference<>(start);
		}

		void set(Instant instant) {
			now.set(instant);
		}

		void advance(Duration duration) {
			now.updateAndGet(i -> i.plus(duration));
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now.get();
		}
	}

	// ------------------------------------------------------------------ evaluator seam (single adaptation point)

	/**
	 * Runs ONE evaluation pass, as the scheduler would on one replica. Thread-safe: the concurrency test calls it from
	 * two threads at once, which stands in for two Triage replicas sharing one database (triage-service.md 7.4).
	 */
	private void runEvaluator() {
		Object result;
		try {
			Class<?> type = Class.forName(EVALUATOR_CLASS);
			Object evaluator = context.getBean(type);
			Method method = type.getMethod(EVALUATOR_METHOD);
			result = method.invoke(evaluator);
		}
		catch (ClassNotFoundException e) {
			throw new IllegalStateException("SLA evaluator " + EVALUATOR_CLASS + " not found. Update EVALUATOR_CLASS / "
					+ "EVALUATOR_METHOD in " + getClass().getSimpleName() + " to the real scheduler bean.", e);
		}
		catch (NoSuchMethodException e) {
			throw new IllegalStateException("SLA evaluator " + EVALUATOR_CLASS + " has no public no-arg method "
					+ EVALUATOR_METHOD + "()", e);
		}
		catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			throw cause instanceof RuntimeException re ? re : new IllegalStateException(cause);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		if (result instanceof Publisher<?> publisher) {
			Mono.from(publisher).then().block(Duration.ofSeconds(30));
		}
	}

	// ------------------------------------------------------------------ HTTP helpers

	/** Sends the API key expected by ADR-07 on every request; mockUser/csrf are leftovers from basic auth. */
	private WebTestClient authenticated() {
		return webTestClient.mutate()
				.responseTimeout(Duration.ofSeconds(20))
				.defaultHeader("X-API-Key", "test-triage-key")
				.build()
				.mutateWith(mockUser())
				.mutateWith(csrf());
	}

	/** Request body whose vitals classify (RN-01) into the requested level. */
	private static Map<String, Object> bodyFor(Level level) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("nombre_completo", "Paciente SLA " + level);
		body.put("edad", 40);
		body.put("frecuencia_cardiaca", 72);
		body.put("presion_sistolica", 120);
		body.put("presion_diastolica", 80);
		body.put("spo2", 99);
		body.put("temperatura", 36.5);
		body.put("dolor", 2);
		body.put("gcs", 15);
		body.put("queja_principal", "Herida por cortar");
		switch (level) {
			case CRITICO -> body.put("frecuencia_cardiaca", 130);
			case URGENTE -> body.put("spo2", 92);
			case URGENTE_2 -> body.put("dolor", 8);
			case NO_URGENTE -> {
				// the baseline vitals above
			}
			case NO_URGENTE_2 -> body.put("queja_principal", "documentos");
		}
		return body;
	}

	/** Registers through RF-01 at the current clock instant and checks the stored level and SLA. */
	private UUID register(Level level) throws Exception {
		byte[] body = authenticated().post().uri(COLLECTION)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.bodyValue(bodyFor(level))
				.exchange()
				.expectStatus().isCreated()
				.expectBody().returnResult().getResponseBody();
		JsonNode created = JSON.readTree(body);
		assertThat(created.path("nivel").asText()).as("test setup: vitals must classify as %s", level)
				.isEqualTo(level.name());
		assertThat(created.path("slaMinutos").asInt()).isEqualTo(level.slaMinutes);
		return UUID.fromString(created.path("id").asText());
	}

	private void transition(UUID id, String target) {
		authenticated().patch().uri(COLLECTION + "/{id}/estado", id)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("estado", target))
				.exchange()
				.expectStatus().isOk();
	}

	// ------------------------------------------------------------------ database helpers

	private void execute(String sql) {
		databaseClient.sql(sql).then().block();
	}

	private record PatientRow(String state, boolean escalated) {
	}

	private PatientRow patient(UUID id) {
		return databaseClient.sql("SELECT estado, escalado FROM paciente WHERE id = :id")
				.bind("id", id)
				.map((row, metadata) -> new PatientRow(
						row.get("estado", String.class),
						Boolean.TRUE.equals(row.get("escalado", Boolean.class))))
				.one()
				.block();
	}

	private record AuditRow(String estadoAnterior, String estadoNuevo, String tipoEvento) {
	}

	private List<AuditRow> auditRows(UUID id) {
		return databaseClient
				.sql("SELECT estado_anterior, estado_nuevo, tipo_evento FROM auditoria_clinica "
						+ "WHERE paciente_id = :id ORDER BY id")
				.bind("id", id)
				.map((row, metadata) -> new AuditRow(
						row.get("estado_anterior", String.class),
						row.get("estado_nuevo", String.class),
						row.get("tipo_evento", String.class)))
				.all()
				.collectList()
				.block();
	}

	private List<AuditRow> escalationAudit(UUID id) {
		return auditRows(id).stream().filter(a -> ESCALAMIENTO.equals(a.tipoEvento())).toList();
	}

	private record OutboxRow(String eventType, int sequence, String payload) {
	}

	private List<OutboxRow> outboxRows(UUID id) {
		return databaseClient
				.sql("SELECT event_type, paciente_sequence, payload FROM outbox_evento "
						+ "WHERE paciente_id = :id ORDER BY paciente_sequence")
				.bind("id", id)
				.map((row, metadata) -> new OutboxRow(
						row.get("event_type", String.class),
						row.get("paciente_sequence", Integer.class),
						row.get("payload", String.class)))
				.all()
				.collectList()
				.block();
	}

	private List<OutboxRow> escalationEvents(UUID id) {
		return outboxRows(id).stream().filter(o -> EVT_02_TYPES.contains(o.eventType())).toList();
	}

	/** Everything an evaluation pass that must NOT escalate has to leave untouched. */
	private record Snapshot(PatientRow patient, int auditCount, int outboxCount) {
	}

	private Snapshot snapshot(UUID id) {
		return new Snapshot(patient(id), auditRows(id).size(), outboxRows(id).size());
	}

	/** The whole effect of one escalation, asserted in one place. */
	private void assertEscalatedExactlyOnce(UUID id, Level level) throws Exception {
		PatientRow row = patient(id);
		assertThat(row.escalated()).as("escalated flag").isTrue();
		assertThat(row.state()).as("escalation does not move the patient out of REGISTRADO").isEqualTo(REGISTRADO);

		assertThat(escalationAudit(id)).as("one ESCALAMIENTO audit row, with equal states (triage-service.md 2.3)")
				.singleElement()
				.satisfies(a -> {
					assertThat(a.estadoAnterior()).isEqualTo(REGISTRADO);
					assertThat(a.estadoNuevo()).isEqualTo(REGISTRADO);
				});

		List<OutboxRow> evt02 = escalationEvents(id);
		assertThat(evt02).as("exactly one EVT-02 outbox row").hasSize(1);
		assertThat(evt02.get(0).sequence()).as("registration is sequence 1, escalation the next one").isEqualTo(2);
		// sequence tracked in outbox_evento.paciente_sequence

		JsonNode event = JSON.readTree(evt02.get(0).payload());
		assertThat(payloadOf(event).path("nivel").asText()).isEqualTo(level.name());
		assertThat(payloadOf(event).path("slaMinutos").asInt()).isEqualTo(level.slaMinutes);
		assertThat(payloadOf(event).hasNonNull("fechaEscalamiento")).isTrue();
	}

	// ------------------------------------------------------------------ Kafka helpers

	private static JsonNode payloadOf(JsonNode event) {
		return event.hasNonNull("payload") ? event.get("payload") : event;
	}

	private static boolean isEvt02(JsonNode event) {
		return EVT_02_TYPES.contains(event.path("eventType").asText());
	}

	/**
	 * Reads {@value #TOPIC} from the beginning until {@code expectedTotal} events for these patients showed up (the
	 * outbox relay is asynchronous), then keeps listening for a settle window to detect duplicates. The topic is never
	 * cleaned between tests, so events are filtered by patient id.
	 */
	private List<JsonNode> consumeEventsFor(Set<UUID> patientIds, int expectedTotal) throws Exception {
		Set<String> ids = new HashSet<>();
		patientIds.forEach(id -> ids.add(id.toString()));

		Map<String, Object> config = Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
				ConsumerConfig.GROUP_ID_CONFIG, "rf03-it-" + UUID.randomUUID(),
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
				ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

		List<ConsumerRecord<String, String>> matches = new ArrayList<>();
		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
			consumer.subscribe(List.of(TOPIC));

			await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200)).until(() -> {
				collect(consumer, ids, matches);
				return matches.size() >= expectedTotal;
			});

			long settleUntil = System.nanoTime() + Duration.ofSeconds(5).toNanos();
			while (System.nanoTime() < settleUntil) {
				collect(consumer, ids, matches);
			}
		}

		List<JsonNode> events = new ArrayList<>();
		for (ConsumerRecord<String, String> record : matches) {
			events.add(JSON.readTree(record.value()));
		}
		return events;
	}

	private List<JsonNode> consumeEventsFor(UUID patientId, int expectedTotal) throws Exception {
		return consumeEventsFor(Set.of(patientId), expectedTotal);
	}

	private static void collect(KafkaConsumer<String, String> consumer, Set<String> patientIds,
			List<ConsumerRecord<String, String>> matches) {
		for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
			boolean mine = patientIds.contains(record.key())
					|| (record.value() != null && patientIds.stream().anyMatch(record.value()::contains));
			if (mine) {
				matches.add(record);
			}
		}
	}

	// ------------------------------------------------------------------ 1. happy path

	@Test
	@DisplayName("SLA exceeded: an overdue CRITICO patient is escalated with one ESCALAMIENTO audit row and one EVT-02")
	void overdueCriticalPatientIsEscalated() throws Exception {
		UUID id = register(Level.CRITICO);
		assertThat(patient(id).escalated()).as("not escalated at registration").isFalse();

		clock.advance(Duration.ofMinutes(6));
		runEvaluator();

		assertEscalatedExactlyOnce(id, Level.CRITICO);

		// EVT-01 (registration) + EVT-02 (escalation), keyed by pacienteId.
		List<JsonNode> events = consumeEventsFor(id, 2);
		assertThat(events).as("exactly 2 events for patient %s on %s", id, TOPIC).hasSize(2);
		assertThat(events.get(0).path("eventType").asText()).isIn(EVT_01_TYPES);

		JsonNode escalation = events.get(1);
		assertThat(isEvt02(escalation)).as("second event is EVT-02").isTrue();
		assertThat(escalation.path("pacienteId").asText()).isEqualTo(id.toString());
		assertThat(escalation.path("pacienteSequence").asLong()).isEqualTo(2L);
		assertThat(escalation.path("schemaVersion").asInt()).isEqualTo(1);
		assertThat(UUID.fromString(escalation.path("eventId").asText())).isNotNull();
		assertThat(payloadOf(escalation).path("nivel").asText()).isEqualTo("CRITICO");
		assertThat(payloadOf(escalation).path("slaMinutos").asInt()).isEqualTo(5);
		assertThat(payloadOf(escalation).hasNonNull("fechaEscalamiento")).isTrue();
		assertThat(escalation.toString()).as("data minimization: no patient name in events")
				.doesNotContain("Paciente SLA").doesNotContain("Herida por cortar");
	}

	@Test
	@DisplayName("AMB-04: elapsed time exactly equal to the SLA is not overdue; one more minute is")
	void elapsedEqualToSlaIsNotOverdue() throws Exception {
		UUID id = register(Level.CRITICO);

		clock.advance(Duration.ofMinutes(5));
		Snapshot before = snapshot(id);
		runEvaluator();
		assertThat(snapshot(id)).as("elapsed == SLA: nothing changes").isEqualTo(before);

		clock.advance(Duration.ofMinutes(1));
		runEvaluator();
		assertEscalatedExactlyOnce(id, Level.CRITICO);
	}

	// ------------------------------------------------------------------ 2. RN-03 idempotence

	@Test
	@DisplayName("RN-03: a second evaluation does not escalate again (no new audit row, no new event, flag stays true)")
	void secondEvaluationDoesNothing() throws Exception {
		UUID id = register(Level.CRITICO);
		clock.advance(Duration.ofMinutes(6));
		runEvaluator();
		assertEscalatedExactlyOnce(id, Level.CRITICO);
		Snapshot afterFirst = snapshot(id);

		clock.advance(Duration.ofMinutes(1));
		runEvaluator();
		runEvaluator();

		assertThat(patient(id).escalated()).as("escalated is never toggled back").isTrue();
		assertThat(snapshot(id)).as("flag, audit rows, outbox rows are unchanged")
				.isEqualTo(afterFirst);
		assertThat(escalationEvents(id)).hasSize(1);
		assertThat(escalationAudit(id)).hasSize(1);

		assertThat(consumeEventsFor(id, 2)).as("EVT-01 + a single EVT-02 on the topic").hasSize(2)
				.filteredOn(TriageSLAEscalationIntegrationTest::isEvt02).hasSize(1);
	}

	@Test
	@DisplayName("RN-03: many later evaluations, hours after the SLA, still produce a single escalation")
	void repeatedEvaluationsOverTimeStayIdempotent() throws Exception {
		UUID id = register(Level.URGENTE);
		clock.advance(Duration.ofMinutes(11));

		for (int pass = 0; pass < 5; pass++) {
			runEvaluator();
			clock.advance(Duration.ofMinutes(30));
		}

		assertEscalatedExactlyOnce(id, Level.URGENTE);
	}

	// ------------------------------------------------------------------ 3. state transition protection

	@Test
	@DisplayName("A patient that entered EN_CONSULTA before the evaluation is never escalated, nor after ALTA")
	void patientsThatLeftRegistradoAreNeverEscalated() throws Exception {
		UUID id = register(Level.CRITICO);
		clock.advance(Duration.ofMinutes(6)); // SLA is exceeded, but the evaluator has not run yet

		transition(id, EN_CONSULTA);
		Snapshot inConsultation = snapshot(id);
		assertThat(inConsultation.patient().state()).isEqualTo(EN_CONSULTA);

		runEvaluator();

		assertThat(patient(id).escalated()).as("EN_CONSULTA is safe").isFalse();
		assertThat(snapshot(id)).as("nothing written by the evaluator").isEqualTo(inConsultation);
		assertThat(escalationEvents(id)).isEmpty();
		assertThat(escalationAudit(id)).isEmpty();

		transition(id, ALTA);
		Snapshot discharged = snapshot(id);
		clock.advance(Duration.ofMinutes(60));

		runEvaluator();

		assertThat(patient(id).escalated()).as("ALTA is final").isFalse();
		assertThat(snapshot(id)).isEqualTo(discharged);
		assertThat(escalationEvents(id)).isEmpty();

		// EVT-01 + EVT-03 + EVT-04, and no EVT-02.
		assertThat(consumeEventsFor(id, 3)).hasSize(3).noneMatch(TriageSLAEscalationIntegrationTest::isEvt02);
	}

	@Test
	@DisplayName("A direct discharge (REGISTRADO -> ALTA) past the SLA is never escalated")
	void directDischargeIsNeverEscalated() throws Exception {
		UUID id = register(Level.URGENTE);
		clock.advance(Duration.ofMinutes(15));
		transition(id, ALTA);
		Snapshot discharged = snapshot(id);

		runEvaluator();

		assertThat(snapshot(id)).isEqualTo(discharged);
		assertThat(patient(id).escalated()).isFalse();
		assertThat(escalationEvents(id)).isEmpty();
	}

	@Test
	@DisplayName("Only the overdue REGISTRADO patient is escalated when others share the run in EN_CONSULTA or ALTA")
	void evaluatorSelectsOnlyRegisteredPatients() throws Exception {
		UUID registered = register(Level.CRITICO);
		UUID inConsultation = register(Level.CRITICO);
		UUID discharged = register(Level.CRITICO);
		transition(inConsultation, EN_CONSULTA);
		transition(discharged, ALTA);

		clock.advance(Duration.ofMinutes(6));
		runEvaluator();

		assertEscalatedExactlyOnce(registered, Level.CRITICO);
		assertThat(patient(inConsultation).escalated()).isFalse();
		assertThat(patient(discharged).escalated()).isFalse();
		assertThat(escalationEvents(inConsultation)).isEmpty();
		assertThat(escalationEvents(discharged)).isEmpty();
	}

	// ------------------------------------------------------------------ 4. concurrency (two replicas)

	@Test
	@DisplayName("Two evaluators racing on the same overdue patients: one winner per patient, one EVT-02, no duplicates")
	void concurrentEvaluatorsProduceOneEscalationPerPatient() throws Exception {
		int patients = 8; // several rows per round make an interleaving of the two passes far more likely
		Map<UUID, Level> registered = new HashMap<>();
		for (int i = 0; i < patients; i++) {
			UUID id = register(Level.CRITICO);
			registered.put(id, Level.CRITICO);
		}
		clock.advance(Duration.ofMinutes(6));

		for (UUID id : registered.keySet()) {
			assertThat(patient(id).escalated()).as("both replicas start from escalated=false").isFalse();
		}

		CyclicBarrier startTogether = new CyclicBarrier(2);
		ExecutorService replicas = Executors.newFixedThreadPool(2);
		try {
			Callable<Void> replica = () -> {
				startTogether.await(10, TimeUnit.SECONDS);
				runEvaluator();
				return null;
			};
			Future<Void> replica1 = replicas.submit(replica);
			Future<Void> replica2 = replicas.submit(replica);

			// Neither replica may fail: losing the conditional update is a normal outcome, not an error.
			replica1.get(60, TimeUnit.SECONDS);
			replica2.get(60, TimeUnit.SECONDS);
		}
		finally {
			replicas.shutdownNow();
		}

		for (Map.Entry<UUID, Level> entry : registered.entrySet()) {
			assertEscalatedExactlyOnce(entry.getKey(), entry.getValue());
		}

		// Both evaluators are done; a third pass confirms the end state is stable.
		runEvaluator();
		for (UUID id : registered.keySet()) {
			assertThat(escalationEvents(id)).hasSize(1);
		}

		List<JsonNode> events = consumeEventsFor(registered.keySet(), patients * 2);
		assertThat(events).as("one EVT-01 and one EVT-02 per patient, no duplicate from the losing replica")
				.hasSize(patients * 2);
		assertThat(events).filteredOn(TriageSLAEscalationIntegrationTest::isEvt02).hasSize(patients)
				.extracting(e -> e.path("pacienteId").asText())
				.doesNotHaveDuplicates();
	}

	// ------------------------------------------------------------------ 5. multiple patients

	@Test
	@DisplayName("Five patients, one per level, all overdue: five EVT-02 with the right nivel and SLA")
	void everyLevelIsEscalatedWithItsOwnNivel() throws Exception {
		Map<UUID, Level> registered = new LinkedHashMap<>();
		for (Level level : Level.values()) {
			registered.put(register(level), level);
		}

		clock.advance(Duration.ofMinutes(121)); // longer than the largest SLA (NO_URGENTE_2, 120 min)
		runEvaluator();

		for (Map.Entry<UUID, Level> entry : registered.entrySet()) {
			assertEscalatedExactlyOnce(entry.getKey(), entry.getValue());
		}

		List<JsonNode> events = consumeEventsFor(registered.keySet(), registered.size() * 2);
		List<JsonNode> escalations = events.stream().filter(TriageSLAEscalationIntegrationTest::isEvt02).toList();
		assertThat(escalations).as("exactly 5 EVT-02 events").hasSize(5);
		assertThat(escalations).extracting(e -> payloadOf(e).path("nivel").asText())
				.containsExactlyInAnyOrder("CRITICO", "URGENTE", "URGENTE_2", "NO_URGENTE", "NO_URGENTE_2");
		for (JsonNode e : escalations) {
			Level level = registered.get(UUID.fromString(e.path("pacienteId").asText()));
			assertThat(payloadOf(e).path("nivel").asText()).isEqualTo(level.name());
			assertThat(payloadOf(e).path("slaMinutos").asInt()).isEqualTo(level.slaMinutes);
		}
	}

	@Test
	@DisplayName("Each patient is judged against its own SLA: at 11 minutes only CRITICO (5) and URGENTE (10) are overdue")
	void onlyThePatientsWhoseOwnSlaExpiredAreEscalated() throws Exception {
		Map<Level, UUID> registered = new LinkedHashMap<>();
		for (Level level : Level.values()) {
			registered.put(level, register(level));
		}

		clock.advance(Duration.ofMinutes(11));
		runEvaluator();

		assertEscalatedExactlyOnce(registered.get(Level.CRITICO), Level.CRITICO);
		assertEscalatedExactlyOnce(registered.get(Level.URGENTE), Level.URGENTE);
		for (Level level : List.of(Level.URGENTE_2, Level.NO_URGENTE, Level.NO_URGENTE_2)) {
			UUID id = registered.get(level);
			assertThat(patient(id).escalated()).as("%s (SLA %d) is not overdue yet", level, level.slaMinutes).isFalse();
			assertThat(escalationEvents(id)).isEmpty();
		}

		// Later, the remaining ones expire in turn; the already escalated ones are not touched again.
		clock.advance(Duration.ofMinutes(20)); // 31 min total: URGENTE_2 (30) is now overdue
		runEvaluator();
		assertEscalatedExactlyOnce(registered.get(Level.URGENTE_2), Level.URGENTE_2);
		assertEscalatedExactlyOnce(registered.get(Level.CRITICO), Level.CRITICO);
		assertThat(patient(registered.get(Level.NO_URGENTE)).escalated()).isFalse();
	}

	// ------------------------------------------------------------------ 6. not due

	@Test
	@DisplayName("No escalation when the SLA has not expired: flag false, no audit row, no EVT-02")
	void patientWithinSlaIsNotEscalated() throws Exception {
		UUID id = register(Level.CRITICO);
		Snapshot before = snapshot(id);

		clock.advance(Duration.ofMinutes(3));
		runEvaluator();

		assertThat(patient(id).escalated()).isFalse();
		assertThat(snapshot(id)).as("the pass writes nothing for a patient within SLA").isEqualTo(before);
		assertThat(escalationEvents(id)).isEmpty();
		assertThat(escalationAudit(id)).isEmpty();

		assertThat(consumeEventsFor(id, 1)).as("only EVT-01 on the topic").hasSize(1)
				.noneMatch(TriageSLAEscalationIntegrationTest::isEvt02);
	}

	@Test
	@DisplayName("Evaluating with an empty database or no overdue patient is a harmless no-op")
	void evaluationWithNothingToDoDoesNotFail() {
		runEvaluator();

		clock.advance(Duration.ofHours(5));
		runEvaluator();
	}

	// ------------------------------------------------------------------ 7. clock injection

	@Test
	@DisplayName("Clock injection: escalation follows the injected Clock, not the wall clock (no sleeping)")
	void escalationFollowsTheInjectedClock() throws Exception {
		UUID id = register(Level.CRITICO);
		long wallClockStart = System.nanoTime();

		clock.advance(Duration.ofMinutes(6));
		runEvaluator();

		assertEscalatedExactlyOnce(id, Level.CRITICO);
		assertThat(Duration.ofNanos(System.nanoTime() - wallClockStart))
				.as("6 simulated minutes elapsed in well under a minute of real time")
				.isLessThan(Duration.ofSeconds(30));
	}

}
