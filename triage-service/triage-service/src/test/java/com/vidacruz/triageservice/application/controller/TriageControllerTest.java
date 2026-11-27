package com.vidacruz.triageservice.application.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.application.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.application.dto.PatientRegistrationResponse;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.TriageMetrics;
import com.vidacruz.triageservice.domain.service.PatientLifecycleService;
import com.vidacruz.triageservice.domain.service.RequestFingerprint;
import com.vidacruz.triageservice.domain.service.TriageClassifier;
import com.vidacruz.triageservice.domain.service.TriageRegistrationService;
import com.vidacruz.triageservice.event.domain.OutboxEvento;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventWriter;
import com.vidacruz.triageservice.infrastructure.config.SlaProperties;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryEntity;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryRepository;
import com.vidacruz.triageservice.infrastructure.persistence.IdempotencyKeyEntity;
import com.vidacruz.triageservice.infrastructure.persistence.IdempotencyKeyRepository;
import com.vidacruz.triageservice.infrastructure.persistence.PatientEntity;
import com.vidacruz.triageservice.infrastructure.persistence.PatientRepository;

import reactor.core.publisher.Mono;

@WebFluxTest(controllers = TriageController.class)
@Import({ TriageRegistrationService.class, PatientLifecycleService.class, TriageClassifier.class,
		SlaProperties.class, TriageControllerTest.TestSecurity.class })
class TriageControllerTest {

	private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

	@MockitoBean
	private PatientRepository patientRepository;

	@MockitoBean
	private AuditEntryRepository auditRepository;

	@MockitoBean
	private OutboxEventWriter eventWriter;

	@MockitoBean
	private MedicalAlertRepository alertRepository;

	@MockitoBean
	private IdempotencyKeyRepository idempotencyRepository;

	@MockitoBean
	private TransactionalOperator transactionalOperator;

	@MockitoBean
	private TriageMetrics metrics;

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
		when(idempotencyRepository.insertPending(anyString(), anyString(), any(Instant.class),
				any(Instant.class))).thenReturn(Mono.just(1));
		when(idempotencyRepository.complete(anyString(), any(UUID.class), anyInt(), anyString()))
				.thenReturn(Mono.just(1));
	}

	@Test
	void registersPatientAndReturnsApiContractBody() {
		client.post().uri("/triage/pacientes")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(registrationJson("Paciente de prueba", 130))
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
	void acceptsCamelCaseAliasesFromThePublishedExamples() {
		client.post().uri("/triage/pacientes")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						  "nombreCompleto": "Paciente alias",
						  "edad": 40,
						  "frecuenciaCardiaca": 130,
						  "presionSistolica": 180,
						  "presionDiastolica": 110,
						  "spo2": 97,
						  "temperatura": 36.8,
						  "dolor": 8,
						  "gcs": 15,
						  "quejaPrincipal": "Dolor torácico"
						}
						""")
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.nivel").isEqualTo("CRITICO");
	}

	@Test
	void invalidRangeReturnsNfr20ErrorBody() {
		client.post().uri("/triage/pacientes")
				.header("X-Correlation-Id", "corr-123")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(registrationJson("Paciente de prueba", 19))
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().valueEquals("X-Correlation-Id", "corr-123")
				.expectBody()
				.jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
				.jsonPath("$.errors[0].campo").isEqualTo("frecuencia_cardiaca")
				.jsonPath("$.errors[0].mensaje").isEqualTo("debe estar entre 20 y 250")
				.jsonPath("$.correlationId").isEqualTo("corr-123");
	}

	@Test
	void replaysStoredResponseForRepeatedKeyAndBody() {
		UUID patientId = UUID.randomUUID();
		Instant registeredAt = Instant.now();
		PatientRegistrationResponse original = new PatientRegistrationResponse(patientId, "CRITICO", 5,
				"REGISTRADO", registeredAt);
		IdempotencyKeyEntity stored = new IdempotencyKeyEntity("idem-1",
				RequestFingerprint.of(registration("Paciente de prueba", 130).toDomain()), patientId, 201,
				writeJson(original), registeredAt, registeredAt);
		when(idempotencyRepository.findById("idem-1")).thenReturn(Mono.just(stored));

		client.post().uri("/triage/pacientes")
				.header("Idempotency-Key", "idem-1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(registrationJson("Paciente de prueba", 130))
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.id").isEqualTo(patientId.toString())
				.jsonPath("$.fechaRegistro").exists();
	}

	@Test
	void keyReusedWithDifferentBodyReturnsConflict() {
		Instant now = Instant.now();
		PatientRegistrationResponse original = new PatientRegistrationResponse(UUID.randomUUID(), "CRITICO", 5,
				"REGISTRADO", now);
		IdempotencyKeyEntity stored = new IdempotencyKeyEntity("idem-2",
				RequestFingerprint.of(registration("Otro paciente", 130).toDomain()), original.id(), 201,
				writeJson(original), now, now);
		when(idempotencyRepository.findById("idem-2")).thenReturn(Mono.just(stored));

		client.post().uri("/triage/pacientes")
				.header("Idempotency-Key", "idem-2")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(registrationJson("Paciente de prueba", 130))
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.CONFLICT)
				.expectBody()
				.jsonPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REUSED")
				.jsonPath("$.correlationId").exists();
	}

	private String writeJson(Object value) {
		try {
			return mapper.writeValueAsString(value);
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static PatientRegistrationRequest registration(String name, int heartRate) {
		return new PatientRegistrationRequest(name, 40, heartRate, 180, 110, 97,
				BigDecimal.valueOf(36.8), 8, 15, "Dolor torácico");
	}

	private static String registrationJson(String name, int heartRate) {
		return """
				{
				  "nombre_completo": "%s",
				  "edad": 40,
				  "frecuencia_cardiaca": %d,
				  "presion_sistolica": 180,
				  "presion_diastolica": 110,
				  "spo2": 97,
				  "temperatura": 36.8,
				  "dolor": 8,
				  "gcs": 15,
				  "queja_principal": "Dolor torácico"
				}
				""".formatted(name, heartRate);
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
