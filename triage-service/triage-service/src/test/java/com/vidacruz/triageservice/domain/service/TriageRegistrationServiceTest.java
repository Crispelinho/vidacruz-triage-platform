package com.vidacruz.triageservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vidacruz.triageservice.application.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.domain.exception.IdempotencyKeyReusedException;
import com.vidacruz.triageservice.domain.exception.InvalidPatientDataException;
import com.vidacruz.triageservice.domain.model.AuditEntry;
import com.vidacruz.triageservice.domain.model.AuditLog;
import com.vidacruz.triageservice.domain.model.EventOutbox;
import com.vidacruz.triageservice.domain.model.IdempotencyRecord;
import com.vidacruz.triageservice.domain.model.IdempotencyStore;
import com.vidacruz.triageservice.domain.model.Patient;
import com.vidacruz.triageservice.domain.model.PatientRegistration;
import com.vidacruz.triageservice.domain.model.PatientStore;
import com.vidacruz.triageservice.domain.model.RegistrationResult;
import com.vidacruz.triageservice.domain.model.RequestFingerprinter;
import com.vidacruz.triageservice.domain.model.TransactionRunner;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.model.TriageMetrics;
import com.vidacruz.triageservice.infrastructure.config.SlaProperties;
import com.vidacruz.triageservice.infrastructure.persistence.JacksonRequestFingerprinter;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TriageRegistrationServiceTest {

	private final RequestFingerprinter fingerprinter = new JacksonRequestFingerprinter();

	@Mock
	private PatientStore patients;

	@Mock
	private AuditLog auditLog;

	@Mock
	private EventOutbox outbox;

	@Mock
	private IdempotencyStore idempotencyStore;

	@Mock
	private TransactionRunner transactions;

	@Mock
	private TriageMetrics metrics;

	private TriageRegistrationService service;

	@BeforeEach
	void setUp() {
		service = new TriageRegistrationService(patients, auditLog, outbox, idempotencyStore, fingerprinter,
				new TriageClassifier(), new SlaProperties(Map.of()), transactions, Clock.systemUTC(), metrics);
		lenient().when(patients.add(any(Patient.class))).thenReturn(Mono.empty());
		lenient().when(auditLog.append(any(AuditEntry.class))).thenReturn(Mono.empty());
		lenient().when(outbox.append(any(UUID.class), anyString(), anyMap(), any(Instant.class)))
				.thenReturn(Mono.empty());
		lenient().when(transactions.inTransaction(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void registersCriticalPatientWritesAuditAndOutboxEvent() {
		StepVerifier.create(service.register(request()))
				.assertNext(result -> {
					assertThat(result.level()).isEqualTo(TriageLevel.CRITICO);
					assertThat(result.slaMinutes()).isEqualTo(5);
					assertThat(result.status()).isEqualTo(TriageRegistrationService.REGISTERED_STATUS);
					assertThat(result.registeredAt()).isNotNull();
				})
				.verifyComplete();

		verify(patients).add(argThat(patient -> patient.level() == TriageLevel.CRITICO && !patient.escalated()));
		verify(auditLog).append(argThat(entry -> TriageRegistrationService.REGISTRATION_AUDIT.equals(entry.eventType())
				&& entry.previousStatus() == null));
		verify(outbox).append(any(UUID.class), eq(TriageRegistrationService.REGISTERED_EVENT), anyMap(),
				any(Instant.class));
		verify(idempotencyStore, never()).reserve(anyString(), anyString(), any(Instant.class), any(Instant.class));
		verify(metrics).patientClassified(TriageLevel.CRITICO);
	}

	@Test
	void registrationRunsInOneTransaction() {
		StepVerifier.create(service.register(request())).expectNextCount(1).verifyComplete();

		verify(transactions).inTransaction(any());
	}

	@Test
	void registeredEventLiteralMatchesThePublishedEventType() {
		assertThat(TriageRegistrationService.REGISTERED_EVENT).isEqualTo("paciente_registrado");

		StepVerifier.create(service.register(request())).expectNextCount(1).verifyComplete();

		verify(outbox).append(any(UUID.class), eq("paciente_registrado"), anyMap(), any(Instant.class));
	}

	@Test
	void replaysOriginalResponseForSameKeyAndBody() {
		RegistrationResult original = original();
		when(idempotencyStore.find("key-1"))
				.thenReturn(Mono.just(new IdempotencyRecord("key-1", fingerprinter.fingerprint(request()), original)));

		StepVerifier.create(service.register(request(), "key-1"))
				.assertNext(result -> assertThat(result).isEqualTo(original))
				.verifyComplete();

		verify(patients, never()).add(any(Patient.class));
		verify(outbox, never()).append(any(UUID.class), anyString(), anyMap(), any(Instant.class));
	}

	@Test
	void replayWaitsForTheWinningReservation() {
		RegistrationResult original = original();
		when(idempotencyStore.find("key-locked")).thenReturn(Mono.empty());
		when(idempotencyStore.reserve(eq("key-locked"), anyString(), any(Instant.class), any(Instant.class)))
				.thenReturn(Mono.just(false));
		when(idempotencyStore.findForUpdate("key-locked")).thenReturn(Mono.just(
				new IdempotencyRecord("key-locked", fingerprinter.fingerprint(request()), original)));

		StepVerifier.create(service.register(request(), "key-locked"))
				.assertNext(result -> assertThat(result.id()).isEqualTo(original.id()))
				.verifyComplete();

		verify(idempotencyStore).findForUpdate("key-locked");
		verify(patients, never()).add(any(Patient.class));
		verify(outbox, never()).append(any(UUID.class), anyString(), anyMap(), any(Instant.class));
	}

	@Test
	void rejectsSameKeyWithDifferentBody() {
		PatientRegistration other = new PatientRegistrationRequest(
				"Otro paciente", 40, 130, 180, 110, 97, BigDecimal.valueOf(36.8), 8, 15, "Dolor torácico").toDomain();
		when(idempotencyStore.find("key-1"))
				.thenReturn(Mono.just(new IdempotencyRecord("key-1", fingerprinter.fingerprint(other), original())));

		StepVerifier.create(service.register(request(), "key-1"))
				.expectError(IdempotencyKeyReusedException.class)
				.verify();

		verify(patients, never()).add(any(Patient.class));
		verify(outbox, never()).append(any(UUID.class), anyString(), anyMap(), any(Instant.class));
	}

	@Test
	void storesResponseWhenKeyIsNew() {
		when(idempotencyStore.find("key-2")).thenReturn(Mono.empty());
		when(idempotencyStore.reserve(eq("key-2"), anyString(), any(Instant.class), any(Instant.class)))
				.thenReturn(Mono.just(true));
		when(idempotencyStore.complete(eq("key-2"), any(RegistrationResult.class))).thenReturn(Mono.empty());

		StepVerifier.create(service.register(request(), "key-2"))
				.assertNext(result -> assertThat(result.id()).isNotNull())
				.verifyComplete();

		verify(idempotencyStore).reserve(eq("key-2"), eq(fingerprinter.fingerprint(request())), any(Instant.class),
				any(Instant.class));
		verify(idempotencyStore).complete(eq("key-2"), argThat(result -> result.level() == TriageLevel.CRITICO
				&& result.slaMinutes() == 5));
		verify(metrics).patientClassified(TriageLevel.CRITICO);
	}

	@Test
	void failedValidationWithKeyNeverReservesTheKey() {
		PatientRegistrationRequest invalid = new PatientRegistrationRequest(
				"Paciente de prueba", 40, 19, 180, 110, 97, BigDecimal.valueOf(36.8), 8, 15, "Dolor torácico");

		// Validation happens when the request becomes a domain registration, before the use case runs.
		assertThatThrownBy(() -> service.register(invalid.toDomain(), "key-3"))
				.isInstanceOf(InvalidPatientDataException.class);

		verify(idempotencyStore, never()).reserve(anyString(), anyString(), any(Instant.class), any(Instant.class));
	}

	private static RegistrationResult original() {
		return new RegistrationResult(UUID.randomUUID(), TriageLevel.CRITICO, 5,
				TriageRegistrationService.REGISTERED_STATUS, Instant.now().minusSeconds(30));
	}

	private static PatientRegistration request() {
		return new PatientRegistrationRequest(
				"Paciente de prueba", 40, 130, 180, 110, 97, BigDecimal.valueOf(36.8), 8, 15, "Dolor torácico")
				.toDomain();
	}
}
