package com.vidacruz.triageservice.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC mapping for the patient table. Database column names follow the Spanish clinical contract.
 * Client-supplied UUIDs require explicit new-state tracking so save performs an INSERT.
 */
@Table("paciente")
public class PatientEntity implements Persistable<UUID> {

	@Id
	private final UUID id;
	@Column("nombre")
	private final String fullName;
	@Column("edad")
	private final Integer age;
	@Column("frecuencia_cardiaca")
	private final Integer heartRate;
	@Column("presion_sistolica")
	private final Integer systolic;
	@Column("presion_diastolica")
	private final Integer diastolic;
	@Column("spo2")
	private final Integer spo2;
	@Column("temperatura")
	private final BigDecimal temperature;
	@Column("dolor")
	private final Integer pain;
	@Column("gcs")
	private final Integer gcs;
	@Column("queja_principal")
	private final String complaint;
	@Column("nivel")
	private final String level;
	@Column("sla_minutos")
	private final Integer slaMinutes;
	@Column("estado")
	private final String status;
	@Column("escalado")
	private final Boolean escalated;
	@Column("fecha_registro")
	private final Instant registeredAt;
	@Column("ultimo_cambio_en")
	private final Instant lastChangedAt;

	@Transient
	private boolean isNew = true;

	@PersistenceCreator
	public PatientEntity(UUID id, String fullName, Integer age, Integer heartRate, Integer systolic,
			Integer diastolic, Integer spo2, BigDecimal temperature, Integer pain, Integer gcs,
			String complaint, String level, Integer slaMinutes, String status, Boolean escalated,
			Instant registeredAt, Instant lastChangedAt) {
		this.id = id;
		this.fullName = fullName;
		this.age = age;
		this.heartRate = heartRate;
		this.systolic = systolic;
		this.diastolic = diastolic;
		this.spo2 = spo2;
		this.temperature = temperature;
		this.pain = pain;
		this.gcs = gcs;
		this.complaint = complaint;
		this.level = level;
		this.slaMinutes = slaMinutes;
		this.status = status;
		this.escalated = escalated;
		this.registeredAt = registeredAt;
		this.lastChangedAt = lastChangedAt;
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	public UUID id() {
		return id;
	}

	public String fullName() {
		return fullName;
	}

	public Integer age() {
		return age;
	}

	public Integer heartRate() {
		return heartRate;
	}

	public Integer systolic() {
		return systolic;
	}

	public Integer diastolic() {
		return diastolic;
	}

	public Integer spo2() {
		return spo2;
	}

	public BigDecimal temperature() {
		return temperature;
	}

	public Integer pain() {
		return pain;
	}

	public Integer gcs() {
		return gcs;
	}

	public String complaint() {
		return complaint;
	}

	public String level() {
		return level;
	}

	public Integer slaMinutes() {
		return slaMinutes;
	}

	public String status() {
		return status;
	}

	public Boolean escalated() {
		return escalated;
	}

	public Instant registeredAt() {
		return registeredAt;
	}

	public Instant lastChangedAt() {
		return lastChangedAt;
	}
}
