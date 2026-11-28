package com.vidacruz.triageservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Patient view returned by API-02/API-03; the entry point maps it to the wire format. */
public record PatientDetail(
		UUID id,
		String fullName,
		TriageLevel level,
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
