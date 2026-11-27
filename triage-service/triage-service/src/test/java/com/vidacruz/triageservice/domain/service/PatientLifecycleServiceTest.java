package com.vidacruz.triageservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vidacruz.triageservice.domain.exception.InvalidStateTransitionException;
import com.vidacruz.triageservice.domain.exception.PatientNotFoundException;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.event.domain.OutboxEvento;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventWriter;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryEntity;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryRepository;
import com.vidacruz.triageservice.infrastructure.persistence.PatientEntity;
import com.vidacruz.triageservice.infrastructure.persistence.PatientRepository;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PatientLifecycleServiceTest {

	@Mock
	private PatientRepository patientRepository;

	@Mock
	private AuditEntryRepository auditRepository;

	@Mock
	private OutboxEventWriter eventWriter;

	@Mock
	private MedicalAlertRepository alertRepository;

	@Mock
	private TransactionalOperator transactionalOperator;

	private PatientLifecycleService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		service = new PatientLifecycleService(patientRepository, auditRepository, eventWriter,
				alertRepository, transactionalOperator,
				Clock.fixed(Instant.parse("2026-03-10T10:00:00Z"), ZoneOffset.UTC));
		lenient().when(alertRepository.resolveForPatient(any(UUID.class), any(Instant.class)))
				.thenReturn(Mono.just(true));
		lenient().when(transactionalOperator.transactional(any(Mono.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void transitionsToConsultationWithAuditAndEvent() {
		UUID patientId = UUID.randomUUID();
		Instant registeredAt = Instant.now().minus(3, ChronoUnit.MINUTES);
		when(patientRepository.findById(patientId)).thenReturn(Mono.just(patient(patientId,
				PatientLifecycleService.REGISTERED, false, registeredAt, registeredAt)));
		when(patientRepository.changeStatus(eq(patientId), eq(PatientLifecycleService.REGISTERED),
				eq(PatientLifecycleService.IN_CONSULTATION), any(Instant.class))).thenReturn(Mono.just(1));
		when(auditRepository.save(any(AuditEntryEntity.class)))
				.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
		when(eventWriter.write(any(UUID.class), anyString(), anyMap(), any(Instant.class)))
				.thenReturn(Mono.just(new OutboxEvento(UUID.randomUUID(), "paciente_en_consulta", "{}", patientId, 1, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC), UUID.randomUUID())));

		StepVerifier.create(service.changeStatus(patientId, PatientLifecycleService.IN_CONSULTATION))
				.assertNext(response -> {
					assertThat(response.status()).isEqualTo(PatientLifecycleService.IN_CONSULTATION);
					assertThat(response.escalated()).isFalse();
					assertThat(response.minutesInStatus()).isZero();
				})
				.verifyComplete();

		verify(auditRepository).save(org.mockito.ArgumentMatchers.argThat(entry ->
				PatientLifecycleService.CONSULTATION_AUDIT.equals(entry.eventType())
						&& PatientLifecycleService.REGISTERED.equals(entry.previousStatus())));
		verify(eventWriter).write(eq(patientId), eq(PatientLifecycleService.CONSULTATION_EVENT),
				anyMap(), any(Instant.class));
		verify(alertRepository).resolveForPatient(eq(patientId), any(Instant.class));
	}

	@Test
	void rejectsTransitionFromDischargedPatient() {
		UUID patientId = UUID.randomUUID();
		when(patientRepository.findById(patientId)).thenReturn(Mono.just(patient(patientId,
				PatientLifecycleService.DISCHARGED, false, Instant.now(), Instant.now())));

		StepVerifier.create(service.changeStatus(patientId, PatientLifecycleService.IN_CONSULTATION))
				.expectError(InvalidStateTransitionException.class)
				.verify();

		verify(auditRepository, never()).save(any(AuditEntryEntity.class));
	}

	@Test
	void missingPatientReturnsNotFound() {
		UUID patientId = UUID.randomUUID();
		when(patientRepository.findById(patientId)).thenReturn(Mono.empty());

		StepVerifier.create(service.getPatient(patientId))
				.expectError(PatientNotFoundException.class)
				.verify();
	}

	@Test
	void detailContainsEveryRegisteredClinicalFieldAndUsesStatusStartForTheTimer() {
		UUID patientId = UUID.randomUUID();
		Instant registeredAt = Instant.parse("2026-03-10T09:50:00Z");
		Instant statusSince = Instant.parse("2026-03-10T09:53:00Z");
		Instant lastChangedAt = Instant.parse("2026-03-10T09:54:00Z"); // escalation, not a state change
		when(patientRepository.findById(patientId)).thenReturn(Mono.just(
				patient(patientId, PatientLifecycleService.REGISTERED, true,
						registeredAt, lastChangedAt, statusSince)));

		JsonNode json = new ObjectMapper().findAndRegisterModules()
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
				.valueToTree(service.getPatient(patientId).block());
		assertThat(json.path("id").asText()).isEqualTo(patientId.toString());
		assertThat(json.path("nombreCompleto").asText()).isEqualTo("Paciente de prueba");
		assertThat(json.path("nivel").asText()).isEqualTo("NO_URGENTE");
		assertThat(json.path("slaMinutos").asInt()).isEqualTo(60);
		assertThat(json.path("estado").asText()).isEqualTo(PatientLifecycleService.REGISTERED);
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

	private static PatientEntity patient(UUID id, String status, boolean escalated, Instant changedAt,
			Instant statusSince) {
		return patient(id, status, escalated, changedAt, changedAt, statusSince);
	}

	private static PatientEntity patient(UUID id, String status, boolean escalated, Instant registeredAt,
			Instant changedAt, Instant statusSince) {
		return new PatientEntity(id, "Paciente de prueba", 40, 80, 120, 80, 98,
				BigDecimal.valueOf(36.5), 2, 15, "Control", "NO_URGENTE", 60,
				status, escalated, registeredAt, changedAt, statusSince);
	}
}
