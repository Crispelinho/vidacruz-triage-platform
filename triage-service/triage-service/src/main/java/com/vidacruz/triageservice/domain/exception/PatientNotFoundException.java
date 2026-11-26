package com.vidacruz.triageservice.domain.exception;

import java.util.UUID;

/** API-02/API-03 patient not found; mapped to HTTP 404. */
public class PatientNotFoundException extends RuntimeException {

	public PatientNotFoundException(UUID patientId) {
		super("Paciente no encontrado: " + patientId);
	}
}
