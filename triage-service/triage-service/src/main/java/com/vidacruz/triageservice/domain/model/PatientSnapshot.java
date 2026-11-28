package com.vidacruz.triageservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Framework-free read model for the clinical patient state used by domain services. */
public record PatientSnapshot(
		UUID id,
		String fullName,
		Integer age,
		Integer heartRate,
		Integer systolic,
		Integer diastolic,
		Integer spo2,
		BigDecimal temperature,
		Integer pain,
		Integer gcs,
		String complaint,
		String level,
		Integer slaMinutes,
		String status,
		Boolean escalated,
		Instant registeredAt,
		Instant lastChangedAt,
		Instant statusSince) {
}
