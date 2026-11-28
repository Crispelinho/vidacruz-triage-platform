package com.vidacruz.triageservice.domain.dto;

import java.time.Instant;
import java.util.UUID;

/** RF-01 response body. Wire keys are applied by the application Jackson mixin. */
public record PatientRegistrationResponse(
		UUID id,
		String level,
		int slaMinutes,
		String status,
		Instant registeredAt) {
}
