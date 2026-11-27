package com.vidacruz.triageservice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vidacruz.triageservice.domain.model.AlertStatus;
import com.vidacruz.triageservice.domain.model.TriageLevel;

import io.r2dbc.spi.Readable;

@ExtendWith(MockitoExtension.class)
class MedicalAlertPersistenceTest {

	private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

	@Mock
	private Readable row;

	@Test
	void mapsTheSpanishSchemaWithoutPatientPersonalData() {
		UUID id = UUID.randomUUID();
		UUID patientId = UUID.randomUUID();
		when(row.get("id", UUID.class)).thenReturn(id);
		when(row.get("patient_id", UUID.class)).thenReturn(patientId);
		when(row.get("nivel", String.class)).thenReturn("URGENTE_2");
		when(row.get("sla_minutos", Integer.class)).thenReturn(30);
		when(row.get("fecha_registro", Instant.class)).thenReturn(NOW.minusSeconds(3600));
		when(row.get("fecha_escalamiento", Instant.class)).thenReturn(NOW);
		when(row.get("minutos_espera", BigDecimal.class)).thenReturn(new BigDecimal("30.00"));
		when(row.get("estado", String.class)).thenReturn("RECONOCIDA");
		when(row.get("responsable", String.class)).thenReturn("Dra. Pérez");
		when(row.get("reconocida_en", Instant.class)).thenReturn(NOW.plusSeconds(10));
		when(row.get("resuelta_en", Instant.class)).thenReturn(null);
		when(row.get("actualizada_en", Instant.class)).thenReturn(NOW.plusSeconds(10));

		var alert = MedicalAlertRowMapper.map(row);

		assertThat(alert.id()).isEqualTo(id);
		assertThat(alert.patientId()).isEqualTo(patientId);
		assertThat(alert.level()).isEqualTo(TriageLevel.URGENTE_2);
		assertThat(alert.status()).isEqualTo(AlertStatus.RECONOCIDA);
		assertThat(alert.isActive()).isTrue();
		assertThat(alert.responsible()).isEqualTo("Dra. Pérez");
		assertThat(alert.resolvedAt()).isNull();
	}

	@Test
	void insertIsIdempotentPerPatient() {
		assertThat(PostgresMedicalAlertRepository.INSERT)
				.contains("ON CONFLICT (patient_id) DO NOTHING")
				.contains("'PENDIENTE'");
	}

	@Test
	void activeAlertsAreOrderedBySeverityThenAge() {
		assertThat(PostgresMedicalAlertRepository.SELECT_ACTIVE)
				.contains("estado IN ('PENDIENTE', 'RECONOCIDA')")
				.contains("CASE nivel")
				.contains("fecha_escalamiento, id");
	}

	@Test
	void acknowledgeAndResolveAreConditionalSoTheyCannotRace() {
		assertThat(PostgresMedicalAlertRepository.ACKNOWLEDGE)
				.contains("estado = 'PENDIENTE'");
		assertThat(PostgresMedicalAlertRepository.RESOLVE)
				.contains("patient_id = :patientId")
				.contains("estado IN ('PENDIENTE', 'RECONOCIDA')");
	}
}
