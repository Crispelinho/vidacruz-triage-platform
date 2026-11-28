package com.vidacruz.triageservice.application.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vidacruz.triageservice.domain.model.PatientDetail;
import com.vidacruz.triageservice.domain.model.RegistrationResult;
import com.vidacruz.triageservice.domain.model.TriageLevel;

class PatientMapperTest {

	private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	@Test
	void detailUsesTheSpanishWireKeys() {
		UUID id = UUID.randomUUID();
		Instant registeredAt = Instant.parse("2026-03-10T09:50:00Z");
		PatientDetail detail = new PatientDetail(id, "Paciente de prueba", TriageLevel.NO_URGENTE, 60, "REGISTRADO",
				true, registeredAt, 7, 40, 80, 120, 80, 98, BigDecimal.valueOf(36.5), 2, 15, "Control");

		JsonNode json = mapper.valueToTree(PatientMapper.toResponse(detail));

		assertThat(json.path("id").asText()).isEqualTo(id.toString());
		assertThat(json.path("nombreCompleto").asText()).isEqualTo("Paciente de prueba");
		assertThat(json.path("nivel").asText()).isEqualTo("NO_URGENTE");
		assertThat(json.path("slaMinutos").asInt()).isEqualTo(60);
		assertThat(json.path("estado").asText()).isEqualTo("REGISTRADO");
		assertThat(json.path("escalado").asBoolean()).isTrue();
		assertThat(json.path("fechaRegistro").asText()).isEqualTo(registeredAt.toString());
		assertThat(json.path("tiempoEnEstadoMinutos").asLong()).isEqualTo(7);
		assertThat(json.path("edad").asInt()).isEqualTo(40);
		assertThat(json.path("frecuenciaCardiaca").asInt()).isEqualTo(80);
		assertThat(json.path("presionSistolica").asInt()).isEqualTo(120);
		assertThat(json.path("presionDiastolica").asInt()).isEqualTo(80);
		assertThat(json.path("spo2").asInt()).isEqualTo(98);
		assertThat(json.path("temperatura").asDouble()).isEqualTo(36.5);
		assertThat(json.path("dolor").asInt()).isEqualTo(2);
		assertThat(json.path("gcs").asInt()).isEqualTo(15);
		assertThat(json.path("quejaPrincipal").asText()).isEqualTo("Control");
	}

	@Test
	void registrationUsesTheSpanishWireKeys() {
		RegistrationResult result = new RegistrationResult(UUID.randomUUID(), TriageLevel.CRITICO, 5, "REGISTRADO",
				Instant.parse("2026-03-10T10:00:00Z"));

		JsonNode json = mapper.valueToTree(PatientMapper.toResponse(result));

		assertThat(json.path("id").asText()).isEqualTo(result.id().toString());
		assertThat(json.path("nivel").asText()).isEqualTo("CRITICO");
		assertThat(json.path("slaMinutos").asInt()).isEqualTo(5);
		assertThat(json.path("estado").asText()).isEqualTo("REGISTRADO");
		assertThat(json.path("fechaRegistro").asText()).isEqualTo("2026-03-10T10:00:00Z");
	}
}
