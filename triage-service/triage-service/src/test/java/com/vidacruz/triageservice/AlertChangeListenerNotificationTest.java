package com.vidacruz.triageservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.vidacruz.triageservice.infrastructure.persistence.AlertChangeListener;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * RF-06 evidence for ADR-15: a committed alert row fires the PostgreSQL trigger and the dedicated LISTEN
 * connection reloads and streams that row. Only the id travels in the NOTIFY payload, never patient data.
 */
@Import(TriageTestContainers.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {"triage.sla.evaluation-enabled=false", "triage.sla.evaluation-interval=PT24H"})
class AlertChangeListenerNotificationTest {

	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	// @ServiceConnection only rewrites the main ConnectionFactory. The listener builds a dedicated one from
	// R2dbcProperties, so the test must point those properties at the same container.
	@DynamicPropertySource
	static void dedicatedListenerConnection(DynamicPropertyRegistry registry) {
		registry.add("spring.r2dbc.url", () -> TriageTestContainers.POSTGRES.getJdbcUrl().replace("jdbc:", "r2dbc:"));
		registry.add("spring.r2dbc.username", TriageTestContainers.POSTGRES::getUsername);
		registry.add("spring.r2dbc.password", TriageTestContainers.POSTGRES::getPassword);
	}

	@Autowired
	private DatabaseClient databaseClient;

	@Autowired
	private AlertChangeListener listener;

	@Test
	void deliversAlertReloadedFromPgNotifyAfterCommit() {
		UUID patientId = UUID.randomUUID();
		UUID alertId = UUID.randomUUID();
		Instant now = Instant.now();

		StepVerifier.create(listener.changes())
			.then(() -> insertPatientAndAlert(patientId, alertId, now).block(TIMEOUT))
			.assertNext(alert -> {
				assertThat(alert.id()).isEqualTo(alertId);
				assertThat(alert.patientId()).isEqualTo(patientId);
				assertThat(alert.level().name()).isEqualTo("CRITICO");
			})
			.thenCancel()
			.verify(TIMEOUT);
	}

	private Mono<Void> insertPatientAndAlert(UUID patientId, UUID alertId, Instant now) {
		Mono<Long> patient = databaseClient.sql("""
				INSERT INTO paciente (id, nombre, edad, frecuencia_cardiaca, presion_sistolica,
				                      presion_diastolica, spo2, temperatura, dolor, gcs,
				                      queja_principal, nivel, sla_minutos, estado, estado_desde, fecha_registro, ultimo_cambio_en)
				VALUES (:id, 'Synthetic Patient', 40, 130, 160, 100, 90, 38.0, 8, 12,
				        'Synthetic complaint', 'CRITICO', 1, 'REGISTRADO', :now, :now, :now)
				""")
			.bind("id", patientId)
			.bind("now", now)
			.fetch()
			.rowsUpdated();

		Mono<Long> alert = databaseClient.sql("""
				INSERT INTO medical_alert (id, patient_id, nivel, sla_minutos, fecha_registro,
				                           fecha_escalamiento, minutos_espera, estado, actualizada_en)
				VALUES (:id, :patientId, 'CRITICO', 1, :now, :now, 1.00, 'PENDIENTE', :now)
				""")
			.bind("id", alertId)
			.bind("patientId", patientId)
			.bind("now", now)
			.fetch()
			.rowsUpdated();

		return patient.then(alert).then();
	}
}
