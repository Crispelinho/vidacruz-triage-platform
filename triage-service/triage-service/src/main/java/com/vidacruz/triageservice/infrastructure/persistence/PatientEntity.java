package com.vidacruz.triageservice.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** R2DBC mapping for the patient table. Database column names follow the Spanish clinical contract. */
@Table("paciente")
public record PatientEntity(
		@Id UUID id,
		@Column("nombre") String fullName,
		@Column("edad") Integer age,
		@Column("frecuencia_cardiaca") Integer heartRate,
		@Column("presion_sistolica") Integer systolic,
		@Column("presion_diastolica") Integer diastolic,
		@Column("spo2") Integer spo2,
		@Column("temperatura") BigDecimal temperature,
		@Column("dolor") Integer pain,
		@Column("gcs") Integer gcs,
		@Column("queja_principal") String complaint,
		@Column("nivel") String level,
		@Column("sla_minutos") Integer slaMinutes,
		@Column("estado") String status,
		@Column("escalado") Boolean escalated,
		@Column("fecha_registro") Instant registeredAt,
		@Column("ultimo_cambio_en") Instant lastChangedAt) {
}
