package com.vidacruz.triageservice.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.vidacruz.triageservice.domain.model.AlertStatus;
import com.vidacruz.triageservice.domain.model.MedicalAlert;
import com.vidacruz.triageservice.domain.model.TriageLevel;

import io.r2dbc.spi.Readable;

/** Maps the Spanish column names of {@code medical_alert} to the RF-06 domain model. */
final class MedicalAlertRowMapper {

	static final String COLUMNS = """
			id, patient_id, nivel, sla_minutos, fecha_registro, fecha_escalamiento,
			minutos_espera, estado, responsable, reconocida_en, resuelta_en, actualizada_en
			""";

	static MedicalAlert map(Readable row) {
		return new MedicalAlert(
				row.get("id", UUID.class),
				row.get("patient_id", UUID.class),
				TriageLevel.valueOf(row.get("nivel", String.class)),
				row.get("sla_minutos", Integer.class),
				row.get("fecha_registro", Instant.class),
				row.get("fecha_escalamiento", Instant.class),
				row.get("minutos_espera", BigDecimal.class),
				AlertStatus.valueOf(row.get("estado", String.class)),
				row.get("responsable", String.class),
				row.get("reconocida_en", Instant.class),
				row.get("resuelta_en", Instant.class),
				row.get("actualizada_en", Instant.class));
	}

	private MedicalAlertRowMapper() {
	}
}
