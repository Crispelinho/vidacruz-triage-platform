package com.vidacruz.triageservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.application.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.application.dto.PatientRegistrationResponse;
import com.vidacruz.triageservice.domain.exception.IdempotencyKeyReusedException;
import com.vidacruz.triageservice.domain.exception.InvalidPatientDataException;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.model.TriageMetrics;
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
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TriageRegistrationServiceTest {

	private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

	@Mock
	private PatientRepository patientRepository;

	@Mock
	private AuditEntryRepository auditRepository;

	@Mock
	private OutboxEventWriter eventWriter;

	@Mock
	private IdempotencyKeyRepository idempotencyRepository;

	@Mock
	private TransactionalOperator transactionalOperator;

	@Mock
	private TriageMetrics metrics;

	private TriageRegistrationService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		service = new TriageRegistrationService(patientRepository, auditRepository, eventWriter,
				idempotencyRepository, mapper, new TriageClassifier(), new SlaProperties(Map.of()),
				transactionalOperator, metrics);
		lenient().when(patientRepository.save(any(PatientEntity.class)))
				.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
		lenient().when(auditRepository.save(any(AuditEntryEntity.class)))
				.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
		lenient().when(eventWriter.write(any(UUID.class), anyString(), anyMap(), any(Instant.class)))
				.thenReturn(Mono.just(new OutboxEvento(UUID.randomUUID(), "paciente_registrado", "{}",
						UUID.randomUUID(), 1, OffsetDateTime.now(), UUID.randomUUID())));
		lenient().when(transactionalOperator.transactional(any(Mono.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void registersCriticalPatientWritesAuditAndOutboxEvent() {
		StepVerifier.create(service.register(request()))
				.assertNext(response -> {
					assertThat(response.level()).isEqualTo("CRITICO");
					assertThat(response.slaMinutes()).isEqualTo(5);
					assertThat(response.status()).isEqualTo(TriageRegistrationService.REGISTERED_STATUS);
					assertThat(response.registeredAt()).isNotNull();
				})
				.verifyComplete();

		verify(patientRepository).save(org.mockito.ArgumentMatchers.argThat(
				entity -> "CRITICO".equals(entity.level()) && !entity.escalated()));
		verify(auditRepository).save(org.mockito.ArgumentMatchers.argThat(
				entry -> TriageRegistrationService.REGISTRATION_AUDIT.equals(entry.eventType())
						&& entry.previousStatus() == null));
		verify(eventWriter).write(any(UUID.class),
				org.mockito.ArgumentMatchers.eq(TriageRegistrationService.REGISTERED_EVENT),
				anyMap(), any(Instant.class));
		verify(idempotencyRepository, never()).insertPending(anyString(), anyString(), any(Instant.class),
				any(Instant.class));
		verify(metrics).patientClassified(TriageLevel.CRITICO);
	}

	@Test
	void registeredEventLiteralMatchesThePublishedEventType() {
		assertThat(TriageRegistrationService.REGISTERED_EVENT).isEqualTo("paciente_registrado");

		StepVerifier.create(service.register(request())).expectNextCount(1).verifyComplete();

		verify(eventWriter).write(any(UUID.class), eq("paciente_registrado"), anyMap(), any(Instant.class));
	}

	@Test
	void replaysOriginalResponseForSameKeyAndBody() {
		PatientRegistrationResponse original = new PatientRegistrationResponse(UUID.randomUUID(), "CRITICO", 5,
				TriageRegistrationService.REGISTERED_STATUS, Instant.now().minusSeconds(30));
		IdempotencyKeyEntity stored = stored("key-1", RequestFingerprint.of(request().toDomain()), original);
		when(idempotencyRepository.findById("key-1")).thenReturn(Mono.just(stored));

		StepVerifier.create(service.register(request(), "key-1"))
				.assertNext(response -> {
					assertThat(response.id()).isEqualTo(original.id());
					assertThat(response.level()).isEqualTo("CRITICO");
					assertThat(response.registeredAt()).isEqualTo(original.registeredAt());
				})
				.verifyComplete();

		verify(patientRepository, never()).save(any(PatientEntity.class));
		verify(eventWriter, never()).write(any(UUID.class), anyString(), anyMap(), any(Instant.class));
	}

	@Test
	void replayWaitsForTheWinningReservation() {
		PatientRegistrationResponse original = new PatientRegistrationResponse(UUID.randomUUID(), "CRITICO", 5,
				TriageRegistrationService.REGISTERED_STATUS, Instant.now().minusSeconds(30));
		IdempotencyKeyEntity stored = stored("key-locked", RequestFingerprint.of(request().toDomain()), original);
		when(idempotencyRepository.findById("key-locked")).thenReturn(Mono.empty());
		when(idempotencyRepository.insertPending(eq("key-locked"), anyString(), any(Instant.class),
				any(Instant.class))).thenReturn(Mono.just(0));
		when(idempotencyRepository.findByIdForUpdate("key-locked")).thenReturn(Mono.just(stored));

		StepVerifier.create(service.register(request(), "key-locked"))
				.assertNext(response -> assertThat(response.id()).isEqualTo(original.id()))
				.verifyComplete();

		verify(idempotencyRepository).findByIdForUpdate("key-locked");
		verify(patientRepository, never()).save(any(PatientEntity.class));
		verify(eventWriter, never()).write(any(UUID.class), anyString(), anyMap(), any(Instant.class));
	}

	@Test
	void rejectsSameKeyWithDifferentBody() {
		PatientRegistrationResponse original = new PatientRegistrationResponse(UUID.randomUUID(), "CRITICO", 5,
				TriageRegistrationService.REGISTERED_STATUS, Instant.now());
		PatientRegistrationRequest other = new PatientRegistrationRequest(
				"Otro paciente", 40, 130, 180, 110, 97, BigDecimal.valueOf(36.8), 8, 15, "Dolor torácico");
		IdempotencyKeyEntity stored = stored("key-1", RequestFingerprint.of(other.toDomain()), original);
		when(idempotencyRepository.findById("key-1")).thenReturn(Mono.just(stored));

		StepVerifier.create(service.register(request(), "key-1"))
				.expectError(IdempotencyKeyReusedException.class)
				.verify();

		verify(patientRepository, never()).save(any(PatientEntity.class));
		verify(eventWriter, never()).write(any(UUID.class), anyString(), anyMap(), any(Instant.class));
	}

	@Test
	void storesResponseWhenKeyIsNew() {
		when(idempotencyRepository.findById("key-2")).thenReturn(Mono.empty());
		when(idempotencyRepository.insertPending(eq("key-2"), anyString(), any(Instant.class),
				any(Instant.class))).thenReturn(Mono.just(1));
		when(idempotencyRepository.complete(eq("key-2"), any(UUID.class), anyInt(), anyString()))
				.thenReturn(Mono.just(1));

		StepVerifier.create(service.register(request(), "key-2"))
				.assertNext(response -> assertThat(response.id()).isNotNull())
				.verifyComplete();

		verify(idempotencyRepository).insertPending(eq("key-2"),
				eq(RequestFingerprint.of(request().toDomain())), any(Instant.class), any(Instant.class));
		verify(idempotencyRepository).complete(eq("key-2"), any(UUID.class), eq(201),
				org.mockito.ArgumentMatchers.argThat(body -> body.contains("\"nivel\"")
						&& body.contains("\"slaMinutos\"") && body.contains("\"fechaRegistro\"")));
		verify(metrics).patientClassified(TriageLevel.CRITICO);
	}

	@Test
	void failedValidationWithKeyNeverReservesTheKey() {
		PatientRegistrationRequest invalid = new PatientRegistrationRequest(
				"Paciente de prueba", 40, 19, 180, 110, 97, BigDecimal.valueOf(36.8), 8, 15, "Dolor torácico");

		assertThatThrownBy(() -> service.register(invalid, "key-3"))
				.isInstanceOf(InvalidPatientDataException.class);

		verify(idempotencyRepository, never()).insertPending(anyString(), anyString(), any(Instant.class),
				any(Instant.class));
	}

	private IdempotencyKeyEntity stored(String key, String hash, PatientRegistrationResponse response) {
		try {
			return new IdempotencyKeyEntity(key, hash, response.id(), 201,
					mapper.writeValueAsString(response), response.registeredAt(), response.registeredAt());
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static PatientRegistrationRequest request() {
		return new PatientRegistrationRequest(
				"Paciente de prueba", 40, 130, 180, 110, 97, BigDecimal.valueOf(36.8), 8, 15, "Dolor torácico");
	}
}
