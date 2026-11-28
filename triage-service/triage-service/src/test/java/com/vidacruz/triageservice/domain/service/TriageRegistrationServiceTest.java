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
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vidacruz.triageservice.application.config.TriageWireModule;
import com.vidacruz.triageservice.domain.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.domain.dto.PatientRegistrationResponse;
import com.vidacruz.triageservice.domain.exception.IdempotencyKeyReusedException;
import com.vidacruz.triageservice.domain.exception.InvalidPatientDataException;
import com.vidacruz.triageservice.domain.model.AuditEntry;
import com.vidacruz.triageservice.domain.model.IdempotencyKey;
import com.vidacruz.triageservice.domain.model.PatientSnapshot;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.model.TriageMetrics;
import com.vidacruz.triageservice.domain.port.AuditStore;
import com.vidacruz.triageservice.domain.port.IdempotencyStore;
import com.vidacruz.triageservice.domain.port.OutboxWriter;
import com.vidacruz.triageservice.domain.port.PatientStore;
import com.vidacruz.triageservice.domain.port.RegistrationResponseCodec;
import com.vidacruz.triageservice.domain.port.SlaPolicy;
import com.vidacruz.triageservice.domain.port.TransactionalExecutor;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TriageRegistrationServiceTest {

	private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
			.registerModule(new TriageWireModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	private final RegistrationResponseCodec responseCodec = new TestRegistrationResponseCodec();

	@Mock
	private PatientStore patientStore;

	@Mock
	private AuditStore auditStore;

	@Mock
	private OutboxWriter outboxWriter;

	@Mock
	private IdempotencyStore idempotencyStore;

	@Mock
	private TransactionalExecutor transactionalExecutor;

	@Mock
	private TriageMetrics metrics;

	@Mock
	private SlaPolicy slaPolicy;

	private TriageRegistrationService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		service = new TriageRegistrationService(patientStore, auditStore, outboxWriter,
				idempotencyStore, responseCodec, new TriageClassifier(), slaPolicy,
				transactionalExecutor, Clock.systemUTC(), metrics);
		lenient().when(patientStore.save(any(PatientSnapshot.class)))
				.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
		lenient().when(auditStore.append(any(AuditEntry.class))).thenReturn(Mono.empty());
		lenient().when(outboxWriter.write(any(UUID.class), anyString(), anyMap(), any(Instant.class)))
				.thenReturn(Mono.empty());
		lenient().when(slaPolicy.minutesFor(any(TriageLevel.class)))
				.thenAnswer(invocation -> ((TriageLevel) invocation.getArgument(0)).defaultSlaMinutes());
		lenient().when(transactionalExecutor.transactional(any(Mono.class)))
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

		verify(patientStore).save(org.mockito.ArgumentMatchers.argThat(
				patient -> "CRITICO".equals(patient.level()) && !patient.escalated()));
		verify(auditStore).append(org.mockito.ArgumentMatchers.argThat(
				entry -> TriageRegistrationService.REGISTRATION_AUDIT.equals(entry.eventType())
						&& entry.previousStatus() == null));
		verify(outboxWriter).write(any(UUID.class),
				org.mockito.ArgumentMatchers.eq(TriageRegistrationService.REGISTERED_EVENT),
				anyMap(), any(Instant.class));
		verify(idempotencyStore, never()).insertPending(anyString(), anyString(), any(Instant.class),
				any(Instant.class));
		verify(metrics).patientClassified(TriageLevel.CRITICO);
	}

	@Test
	void registeredEventLiteralMatchesThePublishedEventType() {
		assertThat(TriageRegistrationService.REGISTERED_EVENT).isEqualTo("paciente_registrado");

		StepVerifier.create(service.register(request())).expectNextCount(1).verifyComplete();

		verify(outboxWriter).write(any(UUID.class), eq("paciente_registrado"), anyMap(), any(Instant.class));
	}

	@Test
	void replaysOriginalResponseForSameKeyAndBody() {
		PatientRegistrationResponse original = new PatientRegistrationResponse(UUID.randomUUID(), "CRITICO", 5,
				TriageRegistrationService.REGISTERED_STATUS, Instant.now().minusSeconds(30));
		IdempotencyKey stored = stored("key-1", RequestFingerprint.of(request().toDomain()), original);
		when(idempotencyStore.findById("key-1")).thenReturn(Mono.just(stored));

		StepVerifier.create(service.register(request(), "key-1"))
				.assertNext(response -> {
					assertThat(response.id()).isEqualTo(original.id());
					assertThat(response.level()).isEqualTo("CRITICO");
					assertThat(response.registeredAt()).isEqualTo(original.registeredAt());
				})
				.verifyComplete();

		verify(patientStore, never()).save(any(PatientSnapshot.class));
		verify(outboxWriter, never()).write(any(UUID.class), anyString(), anyMap(), any(Instant.class));
	}

	@Test
	void replayWaitsForTheWinningReservation() {
		PatientRegistrationResponse original = new PatientRegistrationResponse(UUID.randomUUID(), "CRITICO", 5,
				TriageRegistrationService.REGISTERED_STATUS, Instant.now().minusSeconds(30));
		IdempotencyKey stored = stored("key-locked", RequestFingerprint.of(request().toDomain()), original);
		when(idempotencyStore.findById("key-locked")).thenReturn(Mono.empty());
		when(idempotencyStore.insertPending(eq("key-locked"), anyString(), any(Instant.class),
				any(Instant.class))).thenReturn(Mono.just(0));
		when(idempotencyStore.findByIdForUpdate("key-locked")).thenReturn(Mono.just(stored));

		StepVerifier.create(service.register(request(), "key-locked"))
				.assertNext(response -> assertThat(response.id()).isEqualTo(original.id()))
				.verifyComplete();

		verify(idempotencyStore).findByIdForUpdate("key-locked");
		verify(patientStore, never()).save(any(PatientSnapshot.class));
		verify(outboxWriter, never()).write(any(UUID.class), anyString(), anyMap(), any(Instant.class));
	}

	@Test
	void rejectsSameKeyWithDifferentBody() {
		PatientRegistrationResponse original = new PatientRegistrationResponse(UUID.randomUUID(), "CRITICO", 5,
				TriageRegistrationService.REGISTERED_STATUS, Instant.now());
		PatientRegistrationRequest other = new PatientRegistrationRequest(
				"Otro paciente", 40, 130, 180, 110, 97, BigDecimal.valueOf(36.8), 8, 15, "Dolor torácico");
		IdempotencyKey stored = stored("key-1", RequestFingerprint.of(other.toDomain()), original);
		when(idempotencyStore.findById("key-1")).thenReturn(Mono.just(stored));

		StepVerifier.create(service.register(request(), "key-1"))
				.expectError(IdempotencyKeyReusedException.class)
				.verify();

		verify(patientStore, never()).save(any(PatientSnapshot.class));
		verify(outboxWriter, never()).write(any(UUID.class), anyString(), anyMap(), any(Instant.class));
	}

	@Test
	void storesResponseWhenKeyIsNew() {
		when(idempotencyStore.findById("key-2")).thenReturn(Mono.empty());
		when(idempotencyStore.insertPending(eq("key-2"), anyString(), any(Instant.class),
				any(Instant.class))).thenReturn(Mono.just(1));
		when(idempotencyStore.complete(eq("key-2"), any(UUID.class), anyInt(), anyString()))
				.thenReturn(Mono.just(1));

		StepVerifier.create(service.register(request(), "key-2"))
				.assertNext(response -> assertThat(response.id()).isNotNull())
				.verifyComplete();

		verify(idempotencyStore).insertPending(eq("key-2"),
				eq(RequestFingerprint.of(request().toDomain())), any(Instant.class), any(Instant.class));
		verify(idempotencyStore).complete(eq("key-2"), any(UUID.class), eq(201),
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

		verify(idempotencyStore, never()).insertPending(anyString(), anyString(), any(Instant.class),
				any(Instant.class));
	}

	private IdempotencyKey stored(String key, String hash, PatientRegistrationResponse response) {
		return new IdempotencyKey(key, hash, response.id(), 201,
				responseCodec.encode(response), response.registeredAt(), response.registeredAt());
	}

	private final class TestRegistrationResponseCodec implements RegistrationResponseCodec {

		@Override
		public String encode(PatientRegistrationResponse response) {
			try {
				return mapper.writeValueAsString(response);
			} catch (JsonProcessingException exception) {
				throw new IllegalStateException(exception);
			}
		}

		@Override
		public PatientRegistrationResponse decode(String body) {
			try {
				return mapper.readValue(body, PatientRegistrationResponse.class);
			} catch (JsonProcessingException exception) {
				throw new IllegalStateException(exception);
			}
		}
	}

	private static PatientRegistrationRequest request() {
		return new PatientRegistrationRequest(
				"Paciente de prueba", 40, 130, 180, 110, 97, BigDecimal.valueOf(36.8), 8, 15, "Dolor torácico");
	}
}
