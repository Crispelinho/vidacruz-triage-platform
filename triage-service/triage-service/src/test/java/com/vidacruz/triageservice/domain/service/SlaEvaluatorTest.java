package com.vidacruz.triageservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vidacruz.triageservice.domain.model.AlertStatus;
import com.vidacruz.triageservice.domain.model.AuditEntry;
import com.vidacruz.triageservice.domain.model.AuditLog;
import com.vidacruz.triageservice.domain.model.EventOutbox;
import com.vidacruz.triageservice.domain.model.MedicalAlert;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.Patient;
import com.vidacruz.triageservice.domain.model.PatientStore;
import com.vidacruz.triageservice.domain.model.TransactionRunner;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.model.TriageMetrics;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SlaEvaluatorTest {

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

	@Mock
	private TriageMetrics metrics;

	private SlaEvaluator evaluator;

	@BeforeEach
	void setUp() {
		evaluator = new SlaEvaluator(patients, auditLog, outbox, alertRepository,
				transactions, Clock.fixed(Instant.parse("2026-03-10T10:00:00Z"), ZoneOffset.UTC), metrics);
		lenient().when(alertRepository.create(any(MedicalAlert.class))).thenReturn(Mono.empty());
		lenient().when(transactions.inTransaction(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void escalatesOverduePatientOnce() {
		UUID patientId = UUID.randomUUID();
		Instant registeredAt = Instant.parse("2026-03-10T09:54:00Z");
		when(patients.findEscalationCandidates()).thenReturn(Flux.just(patient(patientId, registeredAt)));
		when(patients.markEscalated(eq(patientId), any(Instant.class))).thenReturn(Mono.just(true));
		when(auditLog.append(any(AuditEntry.class))).thenReturn(Mono.empty());
		when(outbox.append(any(UUID.class), anyString(), anyMap(), any(Instant.class))).thenReturn(Mono.empty());

		StepVerifier.create(evaluator.evaluateOnce())
				.assertNext(count -> assertThat(count).isEqualTo(1))
				.verifyComplete();

		verify(auditLog).append(argThat(entry ->
				SlaEvaluator.ESCALATION_AUDIT.equals(entry.eventType())
						&& PatientLifecycleService.REGISTERED.equals(entry.previousStatus())
						&& PatientLifecycleService.REGISTERED.equals(entry.nextStatus())));
		verify(outbox).append(eq(patientId), eq(SlaEvaluator.ESCALATED_EVENT), anyMap(),
				any(Instant.class));
		verify(alertRepository).create(argThat(alert -> alert.patientId().equals(patientId)
				&& alert.level() == TriageLevel.CRITICO
				&& alert.status() == AlertStatus.PENDIENTE
				&& alert.responsible() == null
				&& alert.waitMinutes().compareTo(BigDecimal.valueOf(6.00)) >= 0));
		verify(metrics).patientEscalated(TriageLevel.CRITICO);
	}

	@Test
	void doesNotEscalateBeforeTheSlaBoundary() {
		UUID patientId = UUID.randomUUID();
		when(patients.findEscalationCandidates()).thenReturn(Flux.just(patient(patientId,
				Instant.parse("2026-03-10T09:56:00Z"))));

		StepVerifier.create(evaluator.evaluateOnce())
				.assertNext(count -> assertThat(count).isZero())
				.verifyComplete();

		verify(patients, never()).markEscalated(any(UUID.class), any(Instant.class));
		verify(auditLog, never()).append(any(AuditEntry.class));
		verify(outbox, never()).append(any(UUID.class), anyString(), anyMap(), any(Instant.class));
		verify(alertRepository, never()).create(any(MedicalAlert.class));
		verify(metrics, never()).patientEscalated(any(TriageLevel.class));
	}

	@Test
	void doesNotWriteSideEffectsWhenConditionalUpdateLosesRace() {
		UUID patientId = UUID.randomUUID();
		Instant registeredAt = Instant.parse("2026-03-10T09:54:00Z");
		when(patients.findEscalationCandidates()).thenReturn(Flux.just(patient(patientId, registeredAt)));
		when(patients.markEscalated(eq(patientId), any(Instant.class))).thenReturn(Mono.just(false));

		StepVerifier.create(evaluator.evaluateOnce())
				.assertNext(count -> assertThat(count).isZero())
				.verifyComplete();

		verify(auditLog, never()).append(any(AuditEntry.class));
		verify(outbox, never()).append(any(UUID.class), anyString(), anyMap(), any(Instant.class));
		verify(alertRepository, never()).create(any(MedicalAlert.class));
		verify(metrics, never()).patientEscalated(any(TriageLevel.class));
	}

	private static Patient patient(UUID id, Instant registeredAt) {
		return new Patient(id, "Paciente de prueba", 40, 80, 120, 80, 98,
				BigDecimal.valueOf(36.5), 2, 15, "Control", TriageLevel.CRITICO, 5,
				PatientLifecycleService.REGISTERED, false, registeredAt, registeredAt, registeredAt);
	}
}
