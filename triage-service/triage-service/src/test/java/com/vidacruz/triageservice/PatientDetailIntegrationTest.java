package com.vidacruz.triageservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.r2dbc.core.DatabaseClient;

import com.vidacruz.triageservice.application.dto.PatientDetailResponse;
import com.vidacruz.triageservice.application.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.application.dto.PatientRegistrationResponse;
import com.vidacruz.triageservice.domain.service.PatientLifecycleService;
import com.vidacruz.triageservice.domain.service.SlaEvaluator;
import com.vidacruz.triageservice.domain.service.TriageRegistrationService;

/** RF-02 detail view: complete clinical data and a time-in-state that survives escalation but resets on transition. */
@Import({TriageTestContainers.class, PatientDetailIntegrationTest.ClockConfig.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {"triage.sla.evaluation-enabled=false", "triage.sla.evaluation-interval=PT24H"})
class PatientDetailIntegrationTest {

	@Autowired
	private DatabaseClient databaseClient;

	@Autowired
	private TriageRegistrationService registrationService;

	@Autowired
	private PatientLifecycleService lifecycleService;

	@Autowired
	private SlaEvaluator evaluator;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void cleanDatabase() {
		truncateTables();
		clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
	}

	@AfterEach
	void removeTestRows() {
		truncateTables();
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.now().truncatedTo(ChronoUnit.MICROS));
		}
	}

	/** UTC clock that only moves when the test advances it, so no test needs to sleep. */
	static final class MutableClock extends Clock {

		private final AtomicReference<Instant> now;

		MutableClock(Instant start) {
			this.now = new AtomicReference<>(start);
		}

		void set(Instant instant) {
			now.set(instant);
		}

		void advance(Duration duration) {
			now.updateAndGet(current -> current.plus(duration));
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

	@Test
	void detailReturnsEveryRegisteredClinicalField() {
		UUID id = register();

		PatientDetailResponse detail = lifecycleService.getPatient(id).block();
		assertThat(detail).isNotNull();
		assertThat(detail.id()).isEqualTo(id);
		assertThat(detail.fullName()).isEqualTo("Paciente de prueba");
		assertThat(detail.level()).isEqualTo("CRITICO");
		assertThat(detail.slaMinutes()).isEqualTo(5);
		assertThat(detail.status()).isEqualTo(PatientLifecycleService.REGISTERED);
		assertThat(detail.escalated()).isFalse();
		assertThat(detail.age()).isEqualTo(40);
		assertThat(detail.heartRate()).isEqualTo(130);
		assertThat(detail.systolic()).isEqualTo(180);
		assertThat(detail.diastolic()).isEqualTo(110);
		assertThat(detail.spo2()).isEqualTo(97);
		assertThat(detail.temperature()).isEqualByComparingTo(BigDecimal.valueOf(36.8));
		assertThat(detail.pain()).isEqualTo(8);
		assertThat(detail.gcs()).isEqualTo(15);
		assertThat(detail.complaint()).isEqualTo("Control");
		assertThat(detail.minutesInStatus()).isZero();
	}

	@Test
	void escalationKeepsTheRegisteredTimerRunning() {
		UUID id = register();
		Instant registeredAt = column(id, "fecha_registro");

		clock.advance(Duration.ofMinutes(6));
		assertThat(evaluator.evaluateOnce().block()).isEqualTo(1);

		PatientDetailResponse detail = lifecycleService.getPatient(id).block();
		assertThat(detail.status()).isEqualTo(PatientLifecycleService.REGISTERED);
		assertThat(detail.escalated()).isTrue();
		assertThat(detail.minutesInStatus()).isEqualTo(6);
		assertThat(column(id, "estado_desde")).isEqualTo(registeredAt);
		assertThat(column(id, "ultimo_cambio_en")).isAfter(registeredAt);
	}

	@Test
	void realStateTransitionsRestartTheTimer() {
		UUID id = register();

		clock.advance(Duration.ofMinutes(3));
		PatientDetailResponse consultation = lifecycleService.changeStatus(id,
				PatientLifecycleService.IN_CONSULTATION).block();
		assertThat(consultation.status()).isEqualTo(PatientLifecycleService.IN_CONSULTATION);
		assertThat(consultation.minutesInStatus()).isZero();

		clock.advance(Duration.ofMinutes(4));
		assertThat(lifecycleService.getPatient(id).block().minutesInStatus()).isEqualTo(4);

		PatientDetailResponse discharge = lifecycleService.changeStatus(id, PatientLifecycleService.DISCHARGED).block();
		assertThat(discharge.status()).isEqualTo(PatientLifecycleService.DISCHARGED);
		assertThat(discharge.minutesInStatus()).isZero();
		Instant lastChange = column(id, "ultimo_cambio_en");
		assertThat(column(id, "estado_desde")).isEqualTo(lastChange);
	}

	private void truncateTables() {
		databaseClient.sql("TRUNCATE TABLE idempotency_key, auditoria_clinica, outbox_evento, "
				+ "medical_alert, paciente CASCADE").then().block();
	}

	private UUID register() {
		PatientRegistrationResponse response = registrationService.register(new PatientRegistrationRequest(
				"Paciente de prueba", 40, 130, 180, 110, 97, BigDecimal.valueOf(36.8), 8, 15, "Control")).block();
		assertThat(response).isNotNull();
		return response.id();
	}

	private Instant column(UUID id, String name) {
		Instant value = databaseClient.sql("SELECT " + name + " AS value FROM paciente WHERE id = :id")
				.bind("id", id)
				.map((row, metadata) -> row.get("value", Instant.class))
				.one()
				.block();
		assertThat(value).as("%s must be mapped", name).isNotNull();
		return value;
	}
}
