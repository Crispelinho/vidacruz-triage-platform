package com.vidacruz.triageservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.report.OperationReport;
import com.vidacruz.triageservice.domain.service.GetOperationReportUseCase;

import reactor.core.publisher.Mono;

/** DB-backed RF-05 evidence: hand-computed fixture and execution plan over the documented indexes. */
@Import(TriageTestContainers.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TriageReportEvidenceTest {

	@Autowired
	private DatabaseClient databaseClient;

	@Autowired
	private TransactionalOperator transactionalOperator;

	@Autowired
	private GetOperationReportUseCase reportUseCase;

	@BeforeEach
	void cleanDatabase() {
		databaseClient.sql("DELETE FROM idempotency_key").then().block();
		databaseClient.sql("DELETE FROM medical_alert").then().block();
		databaseClient.sql("DELETE FROM auditoria_clinica").then().block();
		databaseClient.sql("DELETE FROM outbox_evento").then().block();
		databaseClient.sql("DELETE FROM paciente").then().block();
	}

	@Test
	void reportMatchesHandComputedFixture() {
		Instant hour16 = Instant.parse("2026-11-24T16:00:00Z");
		insertRegisteredPatient(UUID.randomUUID(), "CRITICO", hour16.plusSeconds(600), Duration.ofMinutes(3));
		insertRegisteredPatient(UUID.randomUUID(), "URGENTE", hour16.plusSeconds(1200), Duration.ofMinutes(6));
		insertRegisteredPatient(UUID.randomUUID(), "NO_URGENTE", hour16.plusSeconds(1800), Duration.ofMinutes(9));
		insertRegisteredPatient(UUID.randomUUID(), "URGENTE_2", hour16.plusSeconds(3900), null);

		OperationReport report = reportUseCase.getOperationReport(
				Instant.parse("2026-11-24T00:00:00Z"), Instant.parse("2026-11-24T23:00:00Z")).block();
		assertThat(report).isNotNull();
		assertThat(report.hours()).hasSize(24);

		var registeredAtSixteen = report.hours().stream()
				.filter(hour -> hour.hour().equals(hour16))
				.findFirst().orElseThrow().stats();
		assertThat(registeredAtSixteen.registered()).isEqualTo(3);
		assertThat(registeredAtSixteen.attended()).isEqualTo(3);
		assertThat(registeredAtSixteen.registeredByLevel())
				.containsEntry(TriageLevel.CRITICO, 1L)
				.containsEntry(TriageLevel.URGENTE, 1L)
				.containsEntry(TriageLevel.NO_URGENTE, 1L)
				.containsEntry(TriageLevel.URGENTE_2, 0L)
				.containsEntry(TriageLevel.NO_URGENTE_2, 0L);
		assertThat(registeredAtSixteen.averageWaitMinutes()).isEqualByComparingTo("6.00");
		assertThat(registeredAtSixteen.p95WaitMinutes()).isEqualByComparingTo("8.70");

		var registeredAtSeventeen = report.hours().stream()
				.filter(hour -> hour.hour().equals(hour16.plus(Duration.ofHours(1))))
				.findFirst().orElseThrow().stats();
		assertThat(registeredAtSeventeen.registered()).isEqualTo(1);
		assertThat(registeredAtSeventeen.attended()).isZero();
		assertThat(registeredAtSeventeen.averageWaitMinutes()).isNull();
		assertThat(registeredAtSeventeen.p95WaitMinutes()).isNull();

		assertThat(report.summary().registered()).isEqualTo(4);
		assertThat(report.summary().attended()).isEqualTo(3);
		assertThat(report.summary().averageWaitMinutes()).isEqualByComparingTo("6.00");
		assertThat(report.summary().p95WaitMinutes()).isEqualByComparingTo("8.70");
	}

	@Test
	void reportPlanUsesTheDocumentedIndexes() throws Exception {
		Instant from = Instant.parse("2026-11-24T00:00:00Z");
		Instant to = Instant.parse("2026-11-25T00:00:00Z");
		insertRegisteredPatient(UUID.randomUUID(), "CRITICO", from.plusSeconds(600), Duration.ofMinutes(3));

		Field field = Class.forName("com.vidacruz.triageservice.infrastructure.report.OperationReportSql")
				.getDeclaredField("HOURLY_WITH_SUMMARY");
		field.setAccessible(true);
		String reportPlan = explain((String) field.get(null), from, to);
		assertThat(reportPlan)
				.contains("idx_paciente_fecha_registro")
				.contains("idx_auditoria_tipo_evento_creado_en");
	}

	private String explain(String sql, Instant from, Instant to) {
		return transactionalOperator.transactional(
				databaseClient.sql("SET LOCAL enable_seqscan = off").then()
						.then(Mono.defer(() -> databaseClient.sql("EXPLAIN (ANALYZE, FORMAT JSON) " + sql)
								.bind("from", from)
								.bind("to", to)
								.map((row, metadata) -> row.get(0, String.class))
								.one())))
				.block();
	}

	private void insertRegisteredPatient(UUID id, String level, Instant registeredAt, Duration wait) {
		databaseClient.sql("""
				INSERT INTO paciente (id, nombre, edad, frecuencia_cardiaca, presion_sistolica,
				    presion_diastolica, spo2, temperatura, dolor, gcs, queja_principal, nivel,
				    sla_minutos, estado, escalado, fecha_registro, ultimo_cambio_en)
				VALUES (:id, 'Paciente Prueba', 45, 80, 120, 80, 98, 36.5, 1, 15, 'prueba', :nivel,
				    30, 'REGISTRADO', false, :registrado, :registrado)
				""")
				.bind("id", id)
				.bind("nivel", level)
				.bind("registrado", registeredAt)
				.then().block();
		insertAudit(id, "REGISTRO", registeredAt, "REGISTRADO");
		if (wait != null) {
			insertAudit(id, "ENTRADA_CONSULTA", registeredAt.plus(wait), "EN_CONSULTA");
		}
	}

	private void insertAudit(UUID patientId, String type, Instant createdAt, String status) {
		databaseClient.sql("""
				INSERT INTO auditoria_clinica (id, paciente_id, creado_en, estado_anterior, estado_nuevo, tipo_evento)
				VALUES (:id, :pacienteId, :creadoEn, NULL, :estado, :tipo)
				""")
				.bind("id", UUID.randomUUID())
				.bind("pacienteId", patientId)
				.bind("creadoEn", createdAt)
				.bind("estado", status)
				.bind("tipo", type)
				.then().block();
	}
}
