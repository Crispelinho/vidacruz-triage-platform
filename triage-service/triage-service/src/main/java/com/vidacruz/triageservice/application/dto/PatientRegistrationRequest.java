package com.vidacruz.triageservice.application.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vidacruz.triageservice.domain.model.PatientRegistration;

/** RF-01 request body. Wire keys stay Spanish; Java accessors are English. */
public record PatientRegistrationRequest(
		@JsonProperty("nombre_completo") String fullName,
		@JsonProperty("edad") Integer age,
		@JsonProperty("frecuencia_cardiaca") Integer heartRate,
		@JsonProperty("presion_sistolica") Integer systolic,
		@JsonProperty("presion_diastolica") Integer diastolic,
		@JsonProperty("spo2") Integer spo2,
		@JsonProperty("temperatura") BigDecimal temperature,
		@JsonProperty("dolor") Integer pain,
		@JsonProperty("gcs") Integer gcs,
		@JsonProperty("queja_principal") String complaint) {

	public PatientRegistration toDomain() {
		return new PatientRegistration(
				fullName,
				require(age, "edad"),
				require(heartRate, "frecuencia cardíaca"),
				require(systolic, "presión sistólica"),
				require(diastolic, "presión diastólica"),
				require(spo2, "SpO2"),
				require(temperature, "temperatura").doubleValue(),
				require(pain, "dolor"),
				require(gcs, "GCS"),
				complaint);
	}

	private static <T> T require(T value, String field) {
		if (value == null) {
			throw new com.vidacruz.triageservice.domain.exception.InvalidPatientDataException("campo inválido: " + field);
		}
		return value;
	}
}
