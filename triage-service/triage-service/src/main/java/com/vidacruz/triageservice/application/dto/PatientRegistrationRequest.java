package com.vidacruz.triageservice.application.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vidacruz.triageservice.domain.exception.FieldError;
import com.vidacruz.triageservice.domain.model.PatientRegistration;

/** RF-01 request body. Wire keys stay Spanish; Java accessors are English. */
public record PatientRegistrationRequest(
		@JsonProperty("nombre_completo") @JsonAlias("nombreCompleto") String fullName,
		@JsonProperty("edad") Integer age,
		@JsonProperty("frecuencia_cardiaca") @JsonAlias("frecuenciaCardiaca") Integer heartRate,
		@JsonProperty("presion_sistolica") @JsonAlias("presionSistolica") Integer systolic,
		@JsonProperty("presion_diastolica") @JsonAlias("presionDiastolica") Integer diastolic,
		@JsonProperty("spo2") Integer spo2,
		@JsonProperty("temperatura") BigDecimal temperature,
		@JsonProperty("dolor") Integer pain,
		@JsonProperty("gcs") Integer gcs,
		@JsonProperty("queja_principal") @JsonAlias("quejaPrincipal") String complaint) {

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
			throw new com.vidacruz.triageservice.domain.exception.InvalidPatientDataException(
					message, List.of(new FieldError(field, message)));
		}
		return value;
	}
}
