package com.vidacruz.triageservice.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** API-02/API-03 patient view. Wire keys are applied by the application Jackson mixin. */
public record PatientDetailResponse(
		UUID id,
		String fullName,
		String level,
		int slaMinutes,
		String status,
		boolean escalated,
		Instant registeredAt,
		long minutesInStatus,
		Integer age,
		Integer heartRate,
		Integer systolic,
		Integer diastolic,
		Integer spo2,
		BigDecimal temperature,
		Integer pain,
		Integer gcs,
		String complaint) {
}
