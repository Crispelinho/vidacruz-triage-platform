package com.vidacruz.triageservice.domain.model;

import java.util.List;

import com.vidacruz.triageservice.domain.exception.FieldError;
import com.vidacruz.triageservice.domain.exception.InvalidPatientDataException;

/**
 * Validated vital signs and complaint captured at registration (RF-01). Java names are English; the API field names
 * and messages that identify invalid values stay in Spanish as required by the technical test.
 */
public record PatientRegistration(
		String fullName,
		int age,
		int heartRate,
		int systolic,
		int diastolic,
		int spo2,
		double temperature,
		int pain,
		int gcs,
		String complaint) {

	public PatientRegistration {
		requireText(fullName, "nombre_completo", 3, 100, "debe tener entre 3 y 100 caracteres");
		requireRange(age, "edad", 0, 120, "debe estar entre 0 y 120");
		requireRange(heartRate, "frecuencia_cardiaca", 20, 250, "debe estar entre 20 y 250");
		requireRange(systolic, "presion_sistolica", 60, 260, "debe estar entre 60 y 260");
		requireRange(diastolic, "presion_diastolica", 30, 160, "debe estar entre 30 y 160");
		if (diastolic >= systolic) {
			throw invalid("presion_diastolica", "debe ser menor que la presión sistólica");
		}
		requireRange(spo2, "spo2", 70, 100, "debe estar entre 70 y 100");
		requireRange(temperature, "temperatura", 34.0, 42.0, "debe estar entre 34.0 y 42.0");
		requireRange(pain, "dolor", 0, 10, "debe estar entre 0 y 10");
		requireRange(gcs, "gcs", 3, 15, "debe estar entre 3 y 15");
		if (complaint == null || complaint.isBlank()) {
			throw invalid("queja_principal", "no puede estar vacía");
		}
	}

	private static void requireText(String value, String field, int min, int max, String message) {
		if (value == null || value.trim().length() < min || value.trim().length() > max) {
			throw invalid(field, message);
		}
	}

	private static void requireRange(int value, String field, int min, int max, String message) {
		if (value < min || value > max) {
			throw invalid(field, message);
		}
	}

	private static void requireRange(double value, String field, double min, double max, String message) {
		if (value < min || value > max || Double.isNaN(value)) {
			throw invalid(field, message);
		}
	}

	private static InvalidPatientDataException invalid(String field, String message) {
		return new InvalidPatientDataException(message, List.of(new FieldError(field, message)));
	}
}
