package com.vidacruz.triageservice.application.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.vidacruz.triageservice.domain.service.PatientLifecycleService;
import com.vidacruz.triageservice.domain.service.TriageClassifier;
import com.vidacruz.triageservice.domain.service.TriageRegistrationService;
import com.vidacruz.triageservice.event.domain.OutboxEvento;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventWriter;
import com.vidacruz.triageservice.infrastructure.config.SlaProperties;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryEntity;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryRepository;
import com.vidacruz.triageservice.infrastructure.persistence.PatientEntity;
import com.vidacruz.triageservice.infrastructure.persistence.PatientRepository;

import reactor.core.publisher.Mono;

@WebFluxTest(controllers = TriageController.class)
@Import({ TriageRegistrationService.class, PatientLifecycleService.class, TriageClassifier.class,
		SlaProperties.class, TriageControllerTest.TestSecurity.class })
class TriageControllerTest {

	@MockitoBean
	private PatientRepository patientRepository;

	@MockitoBean
	private AuditEntryRepository auditRepository;

	@MockitoBean
	private OutboxEventWriter eventWriter;

	@MockitoBean
	private TransactionalOperator transactionalOperator;

	@Autowired
	private WebTestClient client;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		when(patientRepository.save(any(PatientEntity.class)))
				.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
		when(auditRepository.save(any(AuditEntryEntity.class)))
				.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
		when(eventWriter.write(any(UUID.class), anyString(), anyMap(), any(Instant.class)))
				.thenReturn(Mono.just(new OutboxEvento(UUID.randomUUID(), "paciente_registrado", "{}",
						UUID.randomUUID(), 1, OffsetDateTime.now(), UUID.randomUUID())));
		when(transactionalOperator.transactional(any(Mono.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void registersPatientAndReturnsApiContractBody() {
		client.post().uri("/triage/pacientes")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "nombre_completo": "Paciente de prueba",
						  "edad": 40,
						  "frecuencia_cardiaca": 130,
						  "presion_sistolica": 180,
						  "presion_diastolica": 110,
						  "spo2": 97,
						  "temperatura": 36.8,
						  "dolor": 8,
						  "gcs": 15,
						  "queja_principal": "Dolor torácico"
						}
						""")
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.id").exists()
				.jsonPath("$.nivel").isEqualTo("CRITICO")
				.jsonPath("$.slaMinutos").isEqualTo(5)
				.jsonPath("$.estado").isEqualTo("REGISTRADO")
				.jsonPath("$.fechaRegistro").exists();
	}

	@Test
	void invalidRangeReturnsBadRequestWithFieldName() {
		client.post().uri("/triage/pacientes")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "nombre_completo": "Paciente de prueba",
						  "edad": 40,
						  "frecuencia_cardiaca": 19,
						  "presion_sistolica": 180,
						  "presion_diastolica": 110,
						  "spo2": 97,
						  "temperatura": 36.8,
						  "dolor": 8,
						  "gcs": 15,
						  "queja_principal": "Dolor torácico"
						}
						""")
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.message").value(containsString("frecuencia cardíaca"));
	}

	@TestConfiguration
	static class TestSecurity {

		@Bean
		SecurityWebFilterChain permitAll(ServerHttpSecurity http) {
			return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
					.authorizeExchange(exchange -> exchange.anyExchange().permitAll())
					.build();
		}
	}
}
