package com.vidacruz.triageservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
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

import com.vidacruz.triageservice.domain.exception.InvalidStateTransitionException;
import com.vidacruz.triageservice.domain.exception.PatientNotFoundException;
import com.vidacruz.triageservice.domain.model.AuditEntry;
import com.vidacruz.triageservice.domain.model.AuditLog;
import com.vidacruz.triageservice.domain.model.EventOutbox;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.Patient;
import com.vidacruz.triageservice.domain.model.PatientDetail;
import com.vidacruz.triageservice.domain.model.PatientStore;
import com.vidacruz.triageservice.domain.model.TransactionRunner;
import com.vidacruz.triageservice.domain.model.TriageLevel;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PatientLifecycleServiceTest {

	@Mock
	private PatientStore patients;

	@Mock
	private AuditLog auditLog;

	@Mock
	private EventOutbox outbox;

	@Mock
	private MedicalAlertRepository alertRepository;

	@Mock
	private TransactionRunner transactions;

	private PatientLifecycleService service;

	@BeforeEach
	void setUp() {
		service = new PatientLifecycleService(patients, auditLog, outbox, alertRepository, transactions,
				Clock.fixed(Instant.parse("2026-03-10T10:00:00Z"), ZoneOffset.UTC));
		lenient().when(alertRepository.resolveForPatient(any(UUID.class), any(Instant.class)))
				.thenReturn(Mono.just(true));
		lenient().when(transactions.inTransaction(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void transitionsToConsultationWithAuditAndEvent() {
		UUID patientId = UUID.randomUUID();
		Instant registeredAt = Instant.now().minus(3, ChronoUnit.MINUTES);
		when(patients.findById(patientId)).thenReturn(Mono.just(patient(patientId,
				PatientLifecycleService.REGISTERED, false, registeredAt, registeredAt)));
		when(patients.changeStatus(eq(patientId), eq(PatientLifecycleService.REGISTERED),
				eq(PatientLifecycleService.IN_CONSULTATION), any(Instant.class))).thenReturn(Mono.just(true));
		when(auditLog.append(any(AuditEntry.class))).thenReturn(Mono.empty());
		when(outbox.append(any(UUID.class), any(), anyMap(), any(Instant.class))).thenReturn(Mono.empty());

		StepVerifier.create(service.changeStatus(patientId, PatientLifecycleService.IN_CONSULTATION))
				.assertNext(detail -> {
					assertThat(detail.status()).isEqualTo(PatientLifecycleService.IN_CONSULTATION);
					assertThat(detail.escalated()).isFalse();
					assertThat(detail.minutesInStatus()).isZero();
				})
				.verifyComplete();

		verify(auditLog).append(argThat(entry ->
				PatientLifecycleService.CONSULTATION_AUDIT.equals(entry.eventType())
						&& PatientLifecycleService.REGISTERED.equals(entry.previousStatus())));
		verify(outbox).append(eq(patientId), eq(PatientLifecycleService.CONSULTATION_EVENT),
				anyMap(), any(Instant.class));
		verify(alertRepository).resolveForPatient(eq(patientId), any(Instant.class));
		verify(transactions).inTransaction(any());
	}

	@Test
	void lostConcurrentTransitionIsRejected() {
		UUID patientId = UUID.randomUUID();
		when(patients.findById(patientId)).thenReturn(Mono.just(patient(patientId,
				PatientLifecycleService.REGISTERED, false, Instant.now(), Instant.now())));
		when(patients.changeStatus(eq(patientId), eq(PatientLifecycleService.REGISTERED),
				eq(PatientLifecycleService.DISCHARGED), any(Instant.class))).thenReturn(Mono.just(false));
		lenient().when(auditLog.append(any(AuditEntry.class))).thenReturn(Mono.empty());
		lenient().when(outbox.append(any(UUID.class), any(), anyMap(), any(Instant.class))).thenReturn(Mono.empty());

		StepVerifier.create(service.changeStatus(patientId, PatientLifecycleService.DISCHARGED))
				.expectError(InvalidStateTransitionException.class)
				.verify();
	}

	@Test
	void rejectsTransitionFromDischargedPatient() {
		UUID patientId = UUID.randomUUID();
		when(patients.findById(patientId)).thenReturn(Mono.just(patient(patientId,
				PatientLifecycleService.DISCHARGED, false, Instant.now(), Instant.now())));

		StepVerifier.create(service.changeStatus(patientId, PatientLifecycleService.IN_CONSULTATION))
				.expectError(InvalidStateTransitionException.class)
				.verify();

		verify(auditLog, never()).append(any(AuditEntry.class));
	}

	@Test
	void missingPatientReturnsNotFound() {
		UUID patientId = UUID.randomUUID();
		when(patients.findById(patientId)).thenReturn(Mono.empty());

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
		when(patients.findById(patientId)).thenReturn(Mono.just(
				patient(patientId, PatientLifecycleService.REGISTERED, true,
						registeredAt, lastChangedAt, statusSince)));

		PatientDetail detail = service.getPatient(patientId).block();

		assertThat(detail).isNotNull();
		assertThat(detail.id()).isEqualTo(patientId);
		assertThat(detail.fullName()).isEqualTo("Paciente de prueba");
		assertThat(detail.level()).isEqualTo(TriageLevel.NO_URGENTE);
		assertThat(detail.slaMinutes()).isEqualTo(60);
		assertThat(detail.status()).isEqualTo(PatientLifecycleService.REGISTERED);
		assertThat(detail.escalated()).isTrue();
		assertThat(detail.registeredAt()).isEqualTo(registeredAt);
		assertThat(detail.minutesInStatus()).isEqualTo(7);
		assertThat(detail.age()).isEqualTo(40);
		assertThat(detail.heartRate()).isEqualTo(80);
		assertThat(detail.systolic()).isEqualTo(120);
		assertThat(detail.diastolic()).isEqualTo(80);
		assertThat(detail.spo2()).isEqualTo(98);
		assertThat(detail.temperature()).isEqualByComparingTo(BigDecimal.valueOf(36.5));
		assertThat(detail.pain()).isEqualTo(2);
		assertThat(detail.gcs()).isEqualTo(15);
		assertThat(detail.complaint()).isEqualTo("Control");
	}

	private static Patient patient(UUID id, String status, boolean escalated, Instant changedAt,
			Instant statusSince) {
		return patient(id, status, escalated, changedAt, changedAt, statusSince);
	}

	private static Patient patient(UUID id, String status, boolean escalated, Instant registeredAt,
			Instant changedAt, Instant statusSince) {
		return new Patient(id, "Paciente de prueba", 40, 80, 120, 80, 98,
				BigDecimal.valueOf(36.5), 2, 15, "Control", TriageLevel.NO_URGENTE, 60,
				status, escalated, registeredAt, changedAt, statusSince);
	}
}
