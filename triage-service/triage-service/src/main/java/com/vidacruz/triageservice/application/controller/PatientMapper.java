package com.vidacruz.triageservice.application.controller;

import com.vidacruz.triageservice.application.dto.PatientDetailResponse;
import com.vidacruz.triageservice.application.dto.PatientRegistrationResponse;
import com.vidacruz.triageservice.domain.model.PatientDetail;
import com.vidacruz.triageservice.domain.model.RegistrationResult;

/** Translates RF-01/RF-02 results to the Spanish HTTP contract. */
final class PatientMapper {

	static PatientRegistrationResponse toResponse(RegistrationResult result) {
		return new PatientRegistrationResponse(result.id(), result.level().name(), result.slaMinutes(),
				result.status(), result.registeredAt());
	}

	static PatientDetailResponse toResponse(PatientDetail detail) {
		return new PatientDetailResponse(
				detail.id(),
				detail.fullName(),
				detail.level().name(),
				detail.slaMinutes(),
				detail.status(),
				detail.escalated(),
				detail.registeredAt(),
				detail.minutesInStatus(),
				detail.age(),
				detail.heartRate(),
				detail.systolic(),
				detail.diastolic(),
				detail.spo2(),
				detail.temperature(),
				detail.pain(),
				detail.gcs(),
				detail.complaint());
	}

	private PatientMapper() {
	}
}
