package com.vidacruz.triageservice.domain.model;

/**
 * Validated vital signs and complaint captured at registration (RF-01). Java names are English; the API messages
 * that name invalid fields stay in Spanish as required by the technical test.
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
		requireText(fullName, "nombre", 3, 100);
		requireRange(age, "edad", 0, 120);
		requireRange(heartRate, "frecuencia cardíaca", 20, 250);
		requireRange(systolic, "presión sistólica", 60, 260);
		requireRange(diastolic, "presión diastólica", 30, 160);
		if (diastolic >= systolic) {
			throw new com.vidacruz.triageservice.domain.exception.InvalidPatientDataException("presión diastólica debe ser menor que la presión sistólica");
		}
		requireRange(spo2, "SpO2", 70, 100);
		requireRange(temperature, "temperatura", 34.0, 42.0);
		requireRange(pain, "dolor", 0, 10);
		requireRange(gcs, "GCS", 3, 15);
		if (complaint == null || complaint.isBlank()) {
			throw new com.vidacruz.triageservice.domain.exception.InvalidPatientDataException("queja principal no puede estar vacía");
		}
	}

	private static void requireText(String value, String field, int min, int max) {
		if (value == null || value.trim().length() < min || value.trim().length() > max) {
			throw new com.vidacruz.triageservice.domain.exception.InvalidPatientDataException("campo inválido: " + field);
		}
	}

	private static void requireRange(int value, String field, int min, int max) {
		if (value < min || value > max) {
			throw new com.vidacruz.triageservice.domain.exception.InvalidPatientDataException("campo inválido: " + field);
		}
	}

	private static void requireRange(double value, String field, double min, double max) {
		if (value < min || value > max || Double.isNaN(value)) {
			throw new com.vidacruz.triageservice.domain.exception.InvalidPatientDataException("campo inválido: " + field);
		}
	}
}
