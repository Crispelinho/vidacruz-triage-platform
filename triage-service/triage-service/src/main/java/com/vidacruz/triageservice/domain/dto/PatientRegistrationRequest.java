package com.vidacruz.triageservice.domain.dto;

import java.math.BigDecimal;
import java.util.List;

import com.vidacruz.triageservice.domain.exception.FieldError;
import com.vidacruz.triageservice.domain.exception.InvalidPatientDataException;
import com.vidacruz.triageservice.domain.model.PatientRegistration;

/** RF-01 request body. Wire keys stay Spanish; Java accessors are English. */
public record PatientRegistrationRequest(
		String fullName,
		Integer age,
		Integer heartRate,
		Integer systolic,
		Integer diastolic,
		Integer spo2,
		BigDecimal temperature,
		Integer pain,
		Integer gcs,
		String complaint) {

	public PatientRegistration toDomain() {
		return new PatientRegistration(
				fullName,
				require(age, "edad"),
				require(heartRate, "frecuencia_cardiaca"),
				require(systolic, "presion_sistolica"),
				require(diastolic, "presion_diastolica"),
				require(spo2, "spo2"),
				require(temperature, "temperatura").doubleValue(),
				require(pain, "dolor"),
				require(gcs, "gcs"),
				complaint);
	}

	private static <T> T require(T value, String field) {
		if (value == null) {
			String message = "no puede estar vacío";
			throw new InvalidPatientDataException(message, List.of(new FieldError(field, message)));
		}
		return value;
	}
}
