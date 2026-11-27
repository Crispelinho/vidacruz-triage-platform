package com.vidacruz.triageservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
import reactor.core.publisher.Mono;

/**
 * RF-05 integration tests: operational SQL reports (operation by hour, latency percentiles, operational SLA).
 *
 * <p>Scope: happy paths only. Each test drives the system through the public API (POST registration, PATCH state
 * transitions) and the SLA evaluator, with a controllable {@link Clock}, and then reads the report endpoints. Error
 * paths (invalid range, SQL failures), paging and large datasets are out of scope.
 *
 * <p>The report endpoints do not exist yet, so this class compiles today and fails until RF-05 is implemented. Every
 * assumption about the not-yet-built contract is concentrated in the constants and the "report contract" helpers
 * below, so adapting it is a one-place edit:
 * <ul>
 *   <li>Endpoints: {@code GET /triage/reports/operation-by-hour}, {@code /latency-percentiles} and
 *       {@code /sla-operational}, with ISO 8601 {@code from} / {@code to} query parameters (UTC, offset or {@code Z}).
 *       Both limits are inclusive at day granularity (requirements.md RF-05, OD-4); the tests use whole-day or
 *       hour-aligned ranges that behave the same under either reading.</li>
 *   <li>Every endpoint answers 200 with a JSON <b>array</b>; with no data it is {@code []}, never {@code null}.</li>
 *   <li>operation-by-hour row: {@code {"hour": <ISO instant>, "total": n, "by_level": {"CRITICO": n, ...}}}. Levels
 *       without patients may be absent or zero.</li>
 *   <li>latency-percentiles row: {@code {"nivel": ..., "p50": m, "p95": m, "p99": m}} where the values are minutes
 *       between registration and the first {@code EN_CONSULTA}, computed with linear interpolation
 *       ({@code percentile_cont}). Patients that never entered consultation are excluded (AMB-05), so a level
 *       without any attended patient has no row.</li>
 *   <li>sla-operational row: {@code {"nivel": ..., "total": n, "escalated_count": n, "escalated_pct": 0..100}}.</li>
 *   <li>Registration and state transitions take "now" from the injected {@link Clock} bean (as the SLA evaluator
 *       already does), so a controllable clock replaces sleeping. If the services keep calling {@code Instant.now()}
 *       the timing based tests cannot be deterministic: wire the Clock first.</li>
 * </ul>
 */
@Import(TriageTestContainers.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"triage.sla.evaluation-enabled=false", "triage.sla.evaluation-interval=PT24H" })
@AutoConfigureWebTestClient
@DisplayName("RF-05: operational SQL reports")
class OperationalReportsIntegrationTest {

	// ------------------------------------------------------------------ report contract (single adaptation point)

	private static final String PATIENTS = "/triage/pacientes";
	private static final String OPERATION_BY_HOUR = "/triage/reports/operation-by-hour";
	private static final String LATENCY_PERCENTILES = "/triage/reports/latency-percentiles";
	private static final String SLA_OPERATIONAL = "/triage/reports/sla-operational";

	private static final String FROM_PARAM = "from";
	private static final String TO_PARAM = "to";

	private static final String HOUR_FIELD = "hour";
	private static final String TOTAL_FIELD = "total";
	private static final String BY_LEVEL_FIELD = "by_level";
	private static final String LEVEL_FIELD = "nivel";
	private static final String ESCALATED_COUNT_FIELD = "escalated_count";
	private static final String ESCALATED_PCT_FIELD = "escalated_pct";

	/** Tolerance for interpolated percentiles and percentages (the SQL may round to 2 decimals). */
	private static final double TOLERANCE = 0.01;

	private static final String EN_CONSULTA = "EN_CONSULTA";

	/** Fixed, hour-aligned base instant so every assertion is deterministic and independent of the wall clock. */
	private static final Instant HOUR_10 = Instant.parse("2026-03-10T10:00:00Z");
	private static final Instant HOUR_11 = HOUR_10.plus(Duration.ofHours(1));

	private static final ObjectMapper JSON = new ObjectMapper();

	/** Triage levels used by the tests (names as stored and serialized). */
	private enum Level {
		CRITICO, URGENTE, URGENTE_2, NO_URGENTE, NO_URGENTE_2
	}

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private DatabaseClient databaseClient;

	@Autowired
	private MutableClock clock;

	@Autowired
	private ApplicationContext context;

	@BeforeEach
	void cleanStateAndResetClock() {
		cleanDatabase();
		clock.set(HOUR_10);
	}

	// ------------------------------------------------------------------ controllable time (same approach as RF-02)

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(HOUR_10);
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

	// ------------------------------------------------------------------ 1. operation by hour

	@Test
	@DisplayName("Operation by hour: 10 patients over 2 hours give the right total and count per level, and from/to narrows the result")
	void operationByHourCountsPerLevelAndHonoursTheRange() throws Exception {
		// Hour 10:00 - 5 patients: 2 CRITICO, 1 URGENTE, 2 NO_URGENTE.
		clock.set(HOUR_10.plus(Duration.ofMinutes(5)));
		register(Level.CRITICO);
		clock.advance(Duration.ofMinutes(10));
		register(Level.CRITICO);
		clock.advance(Duration.ofMinutes(10));
		register(Level.URGENTE);
		clock.advance(Duration.ofMinutes(10));
		register(Level.NO_URGENTE);
		clock.advance(Duration.ofMinutes(10));
		register(Level.NO_URGENTE);

		// Hour 11:00 - 5 patients: 1 CRITICO, 2 URGENTE_2, 1 NO_URGENTE, 1 NO_URGENTE_2.
		clock.set(HOUR_11.plus(Duration.ofMinutes(5)));
		register(Level.CRITICO);
		clock.advance(Duration.ofMinutes(10));
		register(Level.URGENTE_2);
		clock.advance(Duration.ofMinutes(10));
		register(Level.URGENTE_2);
		clock.advance(Duration.ofMinutes(10));
		register(Level.NO_URGENTE);
		clock.advance(Duration.ofMinutes(10));
		register(Level.NO_URGENTE_2);

		// Whole day: two rows, one per hour, ordered by hour.
		JsonNode both = getReport(OPERATION_BY_HOUR, "2026-03-10T00:00:00Z", "2026-03-10T23:59:59Z");
		assertThat(both.isArray()).isTrue();
		assertThat(both).hasSize(2);
		assertThat(hourOf(both.get(0))).isEqualTo(HOUR_10);
		assertThat(hourOf(both.get(1))).isEqualTo(HOUR_11);

		JsonNode first = both.get(0);
		assertThat(first.path(TOTAL_FIELD).asInt()).isEqualTo(5);
		assertThat(countFor(first, Level.CRITICO)).isEqualTo(2);
		assertThat(countFor(first, Level.URGENTE)).isEqualTo(1);
		assertThat(countFor(first, Level.URGENTE_2)).isZero();
		assertThat(countFor(first, Level.NO_URGENTE)).isEqualTo(2);
		assertThat(countFor(first, Level.NO_URGENTE_2)).isZero();

		JsonNode second = both.get(1);
		assertThat(second.path(TOTAL_FIELD).asInt()).isEqualTo(5);
		assertThat(countFor(second, Level.CRITICO)).isEqualTo(1);
		assertThat(countFor(second, Level.URGENTE)).isZero();
		assertThat(countFor(second, Level.URGENTE_2)).isEqualTo(2);
		assertThat(countFor(second, Level.NO_URGENTE)).isEqualTo(1);
		assertThat(countFor(second, Level.NO_URGENTE_2)).isEqualTo(1);

		// Lower bound: starting at 11:00 leaves out the 10:00 hour entirely.
		JsonNode onlyLater = getReport(OPERATION_BY_HOUR, "2026-03-10T11:00:00Z", "2026-03-10T23:59:59Z");
		assertThat(onlyLater).hasSize(1);
		assertThat(hourOf(onlyLater.get(0))).isEqualTo(HOUR_11);
		assertThat(onlyLater.get(0).path(TOTAL_FIELD).asInt()).isEqualTo(5);
	}

	// ------------------------------------------------------------------ 2. latency percentiles

	@Test
	@DisplayName("Latency percentiles: p50/p95/p99 of registration-to-consultation minutes per level (linear interpolation)")
	void latencyPercentilesPerLevel() throws Exception {
		// All ten patients register at the same instant; only the moment each one enters consultation differs.
		Map<UUID, Long> minutesToConsultation = new LinkedHashMap<>();
		for (long minutes : new long[] { 1, 2, 3, 4, 5 }) {
			minutesToConsultation.put(register(Level.CRITICO), minutes);
		}
		for (long minutes : new long[] { 10, 20, 30, 40, 50 }) {
			minutesToConsultation.put(register(Level.URGENTE), minutes);
		}

		List<Map.Entry<UUID, Long>> inOrder = new ArrayList<>(minutesToConsultation.entrySet());
		inOrder.sort(Map.Entry.comparingByValue(Comparator.naturalOrder()));
		for (Map.Entry<UUID, Long> entry : inOrder) {
			clock.set(HOUR_10.plus(Duration.ofMinutes(entry.getValue())));
			transition(entry.getKey(), EN_CONSULTA);
		}

		JsonNode report = getReport(LATENCY_PERCENTILES, "2026-03-10T00:00:00Z", "2026-03-10T23:59:59Z");
		assertThat(report.isArray()).isTrue();
		assertThat(report).hasSize(2);

		// percentile_cont over {1,2,3,4,5}: p50 = 3, p95 = 4.8, p99 = 4.96
		JsonNode critico = rowForLevel(report, Level.CRITICO);
		assertThat(critico.path("p50").asDouble()).isCloseTo(3.0, within(TOLERANCE));
		assertThat(critico.path("p95").asDouble()).isCloseTo(4.8, within(TOLERANCE));
		assertThat(critico.path("p99").asDouble()).isCloseTo(4.96, within(TOLERANCE));

		// percentile_cont over {10,20,30,40,50}: p50 = 30, p95 = 48, p99 = 49.6
		JsonNode urgente = rowForLevel(report, Level.URGENTE);
		assertThat(urgente.path("p50").asDouble()).isCloseTo(30.0, within(TOLERANCE));
		assertThat(urgente.path("p95").asDouble()).isCloseTo(48.0, within(TOLERANCE));
		assertThat(urgente.path("p99").asDouble()).isCloseTo(49.6, within(TOLERANCE));
	}

	// ------------------------------------------------------------------ 3. operational SLA

	@Test
	@DisplayName("Operational SLA: 3 of 10 patients escalated, escalated_count and escalated_pct per level")
	void operationalSlaCountsEscalationsPerLevel() throws Exception {
		// Wave A at 10:00: 2 CRITICO + 1 URGENTE. At 10:11 all three are past their SLA (5 and 10 minutes).
		register(Level.CRITICO);
		register(Level.CRITICO);
		register(Level.URGENTE);

		clock.set(HOUR_10.plus(Duration.ofMinutes(11)));

		// Wave B at 10:11: 2 CRITICO + 2 URGENTE + 3 NO_URGENTE, registered "now", so none is overdue.
		register(Level.CRITICO);
		register(Level.CRITICO);
		register(Level.URGENTE);
		register(Level.URGENTE);
		register(Level.NO_URGENTE);
		register(Level.NO_URGENTE);
		register(Level.NO_URGENTE);

		runEvaluator();
		assertThat(escalatedCount()).as("test setup: exactly the wave A patients are overdue").isEqualTo(3L);

		JsonNode report = getReport(SLA_OPERATIONAL, "2026-03-10T00:00:00Z", "2026-03-10T23:59:59Z");
		assertThat(report.isArray()).isTrue();

		JsonNode critico = rowForLevel(report, Level.CRITICO);
		assertThat(critico.path(TOTAL_FIELD).asInt()).isEqualTo(4);
		assertThat(critico.path(ESCALATED_COUNT_FIELD).asInt()).isEqualTo(2);
		assertThat(critico.path(ESCALATED_PCT_FIELD).asDouble()).isCloseTo(50.0, within(TOLERANCE));

		JsonNode urgente = rowForLevel(report, Level.URGENTE);
		assertThat(urgente.path(TOTAL_FIELD).asInt()).isEqualTo(3);
		assertThat(urgente.path(ESCALATED_COUNT_FIELD).asInt()).isEqualTo(1);
		assertThat(urgente.path(ESCALATED_PCT_FIELD).asDouble()).isCloseTo(33.33, within(TOLERANCE));

		JsonNode noUrgente = rowForLevel(report, Level.NO_URGENTE);
		assertThat(noUrgente.path(TOTAL_FIELD).asInt()).isEqualTo(3);
		assertThat(noUrgente.path(ESCALATED_COUNT_FIELD).asInt()).isZero();
		assertThat(noUrgente.path(ESCALATED_PCT_FIELD).asDouble()).isCloseTo(0.0, within(TOLERANCE));
	}

	// ------------------------------------------------------------------ 4. empty database

	@Test
	@DisplayName("Empty database: every report answers 200 with an empty array, no nulls and no errors")
	void emptyDatabaseYieldsEmptyLists() throws Exception {
		for (String endpoint : List.of(OPERATION_BY_HOUR, LATENCY_PERCENTILES, SLA_OPERATIONAL)) {
			JsonNode report = getReport(endpoint, "2026-03-10T00:00:00Z", "2026-03-10T23:59:59Z");
			assertThat(report).as("%s body", endpoint).isNotNull();
			assertThat(report.isNull()).as("%s must not answer null", endpoint).isFalse();
			assertThat(report.isArray()).as("%s must answer an array", endpoint).isTrue();
			assertThat(report).as("%s has no rows", endpoint).isEmpty();
		}
	}

	// ------------------------------------------------------------------ 5. edge cases

	@Test
	@DisplayName("Edge cases: range without patients, one unattended patient, and all patients on the same level")
	void edgeCases() throws Exception {
		// (a) A range that does not contain the registered patients is empty on every endpoint.
		register(Level.URGENTE);
		for (String endpoint : List.of(OPERATION_BY_HOUR, LATENCY_PERCENTILES, SLA_OPERATIONAL)) {
			JsonNode otherDay = getReport(endpoint, "2026-03-11T00:00:00Z", "2026-03-11T23:59:59Z");
			assertThat(otherDay.isArray()).as("%s body", endpoint).isTrue();
			assertThat(otherDay).as("%s outside the registered day", endpoint).isEmpty();
		}

		// (b) A single patient who never entered consultation is counted by hour but has no percentile group (AMB-05).
		JsonNode byHour = getReport(OPERATION_BY_HOUR, "2026-03-10T00:00:00Z", "2026-03-10T23:59:59Z");
		assertThat(byHour).hasSize(1);
		assertThat(byHour.get(0).path(TOTAL_FIELD).asInt()).isEqualTo(1);
		assertThat(countFor(byHour.get(0), Level.URGENTE)).isEqualTo(1);

		JsonNode percentiles = getReport(LATENCY_PERCENTILES, "2026-03-10T00:00:00Z", "2026-03-10T23:59:59Z");
		assertThat(percentiles.isArray()).isTrue();
		assertThat(percentiles).as("an unattended patient contributes no latency sample").isEmpty();

		// (c) Every patient on the same level: a single group, with all the volume on that level.
		cleanDatabase();
		clock.set(HOUR_10);
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			ids.add(register(Level.CRITICO));
		}
		clock.set(HOUR_10.plus(Duration.ofMinutes(4)));
		for (UUID id : ids) {
			transition(id, EN_CONSULTA);
		}

		JsonNode sameLevelByHour = getReport(OPERATION_BY_HOUR, "2026-03-10T00:00:00Z", "2026-03-10T23:59:59Z");
		assertThat(sameLevelByHour).hasSize(1);
		assertThat(sameLevelByHour.get(0).path(TOTAL_FIELD).asInt()).isEqualTo(3);
		assertThat(countFor(sameLevelByHour.get(0), Level.CRITICO)).isEqualTo(3);
		for (Level other : List.of(Level.URGENTE, Level.URGENTE_2, Level.NO_URGENTE, Level.NO_URGENTE_2)) {
			assertThat(countFor(sameLevelByHour.get(0), other)).as("%s count", other).isZero();
		}

		JsonNode sameLevelLatency = getReport(LATENCY_PERCENTILES, "2026-03-10T00:00:00Z", "2026-03-10T23:59:59Z");
		assertThat(sameLevelLatency).hasSize(1);
		JsonNode critico = rowForLevel(sameLevelLatency, Level.CRITICO);
		assertThat(critico.path("p50").asDouble()).isCloseTo(4.0, within(TOLERANCE));
		assertThat(critico.path("p95").asDouble()).isCloseTo(4.0, within(TOLERANCE));
		assertThat(critico.path("p99").asDouble()).isCloseTo(4.0, within(TOLERANCE));

		JsonNode sameLevelSla = getReport(SLA_OPERATIONAL, "2026-03-10T00:00:00Z", "2026-03-10T23:59:59Z");
		assertThat(sameLevelSla).hasSize(1);
		JsonNode slaRow = rowForLevel(sameLevelSla, Level.CRITICO);
		assertThat(slaRow.path(TOTAL_FIELD).asInt()).isEqualTo(3);
		assertThat(slaRow.path(ESCALATED_COUNT_FIELD).asInt()).isZero();
		assertThat(slaRow.path(ESCALATED_PCT_FIELD).asDouble()).isCloseTo(0.0, within(TOLERANCE));
	}

	// ------------------------------------------------------------------ report contract helpers

	private JsonNode getReport(String endpoint, String from, String to) throws Exception {
		byte[] body = authenticated().get()
				.uri(uriBuilder -> uriBuilder.path(endpoint)
						.queryParam(FROM_PARAM, from)
						.queryParam(TO_PARAM, to)
						.build())
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus().isOk()
				.expectBody().returnResult().getResponseBody();
		assertThat(body).as("%s response body", endpoint).isNotNull();
		return JSON.readTree(body);
	}

	private static Instant hourOf(JsonNode row) {
		return OffsetDateTime.parse(row.path(HOUR_FIELD).asText()).toInstant();
	}

	/** Count for one level in an operation-by-hour row; a missing level counts as zero. */
	private static int countFor(JsonNode row, Level level) {
		return row.path(BY_LEVEL_FIELD).path(level.name()).asInt(0);
	}

	private static JsonNode rowForLevel(JsonNode report, Level level) {
		for (JsonNode row : report) {
			if (level.name().equals(row.path(LEVEL_FIELD).asText())) {
				return row;
			}
		}
		throw new AssertionError("no row for level " + level + " in " + report);
	}

	// ------------------------------------------------------------------ HTTP helpers

	/** Single place to adapt when the real security setup (API key, DEC-05 / ADR-07) is implemented. */
	private WebTestClient authenticated() {
		return webTestClient.mutate()
				.responseTimeout(Duration.ofSeconds(20))
				.build()
				.mutateWith(mockUser())
				.mutateWith(csrf());
	}

	/** Request body whose vitals classify (RN-01) into the requested level. Wire keys follow the RF-01 DTO. */
	private static Map<String, Object> bodyFor(Level level) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("nombre_completo", "Paciente Reporte " + level);
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

	/** Registers through RF-01 at the current clock instant and checks the stored level. */
	private UUID register(Level level) throws Exception {
		byte[] body = authenticated().post().uri(PATIENTS)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.bodyValue(bodyFor(level))
				.exchange()
				.expectStatus().isCreated()
				.expectBody().returnResult().getResponseBody();
		JsonNode created = JSON.readTree(body);
		assertThat(created.path("nivel").asText()).as("test setup: vitals must classify as %s", level)
				.isEqualTo(level.name());
		return UUID.fromString(created.path("id").asText());
	}

	private void transition(UUID id, String target) {
		authenticated().patch().uri(PATIENTS + "/{id}/estado", id)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("estado", target))
				.exchange()
				.expectStatus().isOk();
	}

	// ------------------------------------------------------------------ SLA evaluator seam (adapt here if it moves)

	private static final String EVALUATOR_CLASS = "com.vidacruz.triage.domain.service.SlaEscalationService";
	private static final String EVALUATOR_METHOD = "evaluate";

	/** Runs ONE evaluation pass, as the scheduler would. Looked up reflectively so this class compiles on its own. */
	private void runEvaluator() {
		Object result;
		try {
			Class<?> type = Class.forName(EVALUATOR_CLASS);
			Method method = type.getMethod(EVALUATOR_METHOD);
			result = method.invoke(context.getBean(type));
		}
		catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			throw cause instanceof RuntimeException re ? re : new IllegalStateException(cause);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("SLA evaluator " + EVALUATOR_CLASS + "." + EVALUATOR_METHOD
					+ "() not found; update the constants in " + getClass().getSimpleName(), e);
		}
		if (result instanceof Publisher<?> publisher) {
			Mono.from(publisher).then().block(Duration.ofSeconds(30));
		}
	}

	private Long escalatedCount() {
		return databaseClient.sql("SELECT COUNT(*) AS n FROM paciente WHERE escalado = TRUE")
				.map((row, metadata) -> row.get("n", Long.class))
				.one()
				.block();
	}

	// ------------------------------------------------------------------ database helpers

	private void cleanDatabase() {
		// Isolation: reports aggregate the whole table, so no test may see rows from another one.
		databaseClient.sql("TRUNCATE TABLE auditoria_clinica, outbox_evento, paciente CASCADE").then().block();
	}

}
