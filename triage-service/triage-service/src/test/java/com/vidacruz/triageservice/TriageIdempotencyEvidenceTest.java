package com.vidacruz.triageservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;

import com.vidacruz.triageservice.application.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.application.dto.PatientRegistrationResponse;
import com.vidacruz.triageservice.domain.service.TriageRegistrationService;
import com.vidacruz.triageservice.infrastructure.persistence.IdempotencyKeyEntity;
import com.vidacruz.triageservice.infrastructure.persistence.IdempotencyKeyRepository;
import com.vidacruz.triageservice.infrastructure.persistence.PatientEntity;
import com.vidacruz.triageservice.infrastructure.persistence.PatientRepository;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/** DB-backed evidence for TST-IDM against the shared PostgreSQL test container. */
@Import(TriageTestContainers.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TriageIdempotencyEvidenceTest {

	@Autowired
	private DatabaseClient databaseClient;

	@Autowired
	private TriageRegistrationService registrationService;

	@Autowired
	private IdempotencyKeyRepository idempotencyRepository;

	@Autowired
	private PatientRepository patientRepository;

	@BeforeEach
	void cleanDatabase() {
		databaseClient.sql("DELETE FROM idempotency_key").then().block();
		databaseClient.sql("DELETE FROM auditoria_clinica").then().block();
		databaseClient.sql("DELETE FROM outbox_evento").then().block();
		databaseClient.sql("DELETE FROM paciente").then().block();
	}

	@Test
	void repositorySavePersistsPatient() {
		UUID id = UUID.randomUUID();
		Instant now = Instant.now();
		PatientEntity saved = patientRepository.save(new PatientEntity(
				id, "Ana", 40, 80, 120, 80, 98, new BigDecimal("36.5"), 2, 15, "prueba",
				"VERDE", 30, "REGISTRADO", false, now, now, now)).block();
		assertThat(saved).isNotNull();
		assertThat(patientRepository.findById(id).block()).isNotNull();
		assertThat(count("SELECT COUNT(*) AS n FROM paciente WHERE id = :id", "id", id)).isEqualTo(1);
	}

	@Test
	void sequentialRegistrationPersistsPatient() {
		PatientRegistrationResponse response = registrationService.register(validRequest(), "seq-key").block();
		assertThat(response).isNotNull();
		assertThat(count("SELECT COUNT(*) AS n FROM paciente WHERE id = :id", "id", response.id())).isEqualTo(1);
	}

	@Test
	void concurrentReplayKeepsOnePatientOneAuditAndOneEventWithTwentyFourHourExpiry() {
		String key = "db-idempotency-key";
		Instant now = Instant.now();

		List<PatientRegistrationResponse> responses = Flux.merge(
				registrationService.register(validRequest(), key),
				registrationService.register(validRequest(), key))
				.collectList().block();

		assertThat(responses).hasSize(2);
		UUID patientId = responses.get(0).id();
		assertThat(responses.get(1).id()).isEqualTo(patientId);
		assertThat(count("SELECT COUNT(*) AS n FROM paciente WHERE id = :id", "id", patientId)).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) AS n FROM auditoria_clinica "
				+ "WHERE paciente_id = :id AND tipo_evento = 'REGISTRO'", "id", patientId)).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) AS n FROM outbox_evento "
				+ "WHERE paciente_id = :id AND event_type = 'paciente_registrado'", "id", patientId)).isEqualTo(1);

		IdempotencyKeyEntity stored = idempotencyRepository.findById(key).block();
		assertThat(stored).isNotNull();
		assertThat(stored.responseStatus()).isEqualTo(201);
		assertThat(stored.responseBody()).contains(patientId.toString());
		assertThat(Duration.between(stored.createdAt(), stored.expiresAt())).isEqualTo(Duration.ofHours(24));
		assertThat(stored.expiresAt()).isAfter(now);
	}

	@Test
	void primaryKeySerializesConcurrentReservations() {
		Instant now = Instant.now();
		StepVerifier.create(idempotencyRepository.insertPending("same-key", "hash-a", now, now.plusSeconds(60)))
				.expectNext(1)
				.verifyComplete();

		StepVerifier.create(idempotencyRepository.insertPending("same-key", "hash-b", now, now.plusSeconds(60)))
				.expectNext(0)
				.verifyComplete();
		assertThat(idempotencyRepository.findById("same-key").block().requestHash().trim()).isEqualTo("hash-a");
	}

	@Test
	void cleanupDeletesOnlyExpiredKeys() {
		Instant now = Instant.parse("2026-11-26T12:00:00Z");
		idempotencyRepository.insertPending("expired", "hash-expired",
				now.minus(Duration.ofHours(25)), now.minus(Duration.ofHours(1))).block();
		idempotencyRepository.insertPending("active", "hash-active",
				now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(23))).block();

		StepVerifier.create(idempotencyRepository.deleteExpired(now))
				.expectNext(1)
				.verifyComplete();
		assertThat(idempotencyRepository.findById("expired").block()).isNull();
		assertThat(idempotencyRepository.findById("active").block()).isNotNull();
	}

	private static PatientRegistrationRequest validRequest() {
		return new PatientRegistrationRequest(
				"Ana Perez", 45, 80, 120, 80, 98, new BigDecimal("36.5"), 2, 15, "dolor de pecho");
	}

	private long count(String sql, String parameter, Object value) {
		Long result = databaseClient.sql(sql)
				.bind(parameter, value)
				.map((row, metadata) -> row.get("n", Long.class))
				.one().block();
		return result == null ? 0L : result;
	}
}
