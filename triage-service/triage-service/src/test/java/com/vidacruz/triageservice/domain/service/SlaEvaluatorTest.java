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

import com.vidacruz.triageservice.domain.model.AuditEntry;
import com.vidacruz.triageservice.domain.model.AlertStatus;
import com.vidacruz.triageservice.domain.model.MedicalAlert;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.PatientSnapshot;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.model.TriageMetrics;
import com.vidacruz.triageservice.domain.port.AuditStore;
import com.vidacruz.triageservice.domain.port.OutboxWriter;
import com.vidacruz.triageservice.domain.port.PatientStore;
import com.vidacruz.triageservice.domain.port.TransactionalExecutor;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SlaEvaluatorTest {

	@Mock
	private PatientStore patientStore;

	@Mock
	private AuditStore auditStore;

	@Mock
	private OutboxWriter outboxWriter;

	@Mock
	private MedicalAlertRepository alertRepository;

	@Mock
	private TransactionalExecutor transactionalExecutor;

	@Mock
	private TriageMetrics metrics;

	private SlaEvaluator evaluator;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		evaluator = new SlaEvaluator(patientStore, auditStore, outboxWriter, alertRepository,
				transactionalExecutor, Clock.fixed(Instant.parse("2026-03-10T10:00:00Z"), ZoneOffset.UTC), metrics);
		lenient().when(alertRepository.create(any(MedicalAlert.class))).thenReturn(Mono.empty());
		lenient().when(auditStore.append(any(AuditEntry.class))).thenReturn(Mono.empty());
		lenient().when(outboxWriter.write(any(UUID.class), anyString(), anyMap(), any(Instant.class)))
				.thenReturn(Mono.empty());
		lenient().when(transactionalExecutor.transactional(any(Mono.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void escalatesOverduePatientOnce() {
		UUID patientId = UUID.randomUUID();
		Instant registeredAt = Instant.parse("2026-03-10T09:54:00Z");
		when(patientStore.findEscalationCandidates()).thenReturn(Flux.just(patient(patientId, registeredAt)));
		when(patientStore.escalate(eq(patientId), any(Instant.class))).thenReturn(Mono.just(1));
		when(auditStore.append(any(AuditEntry.class))).thenReturn(Mono.empty());
		when(outboxWriter.write(any(UUID.class), anyString(), anyMap(), any(Instant.class)))
				.thenReturn(Mono.empty());

		StepVerifier.create(evaluator.evaluateOnce())
				.assertNext(count -> assertThat(count).isEqualTo(1))
				.verifyComplete();

		verify(auditStore).append(argThat(entry ->
				SlaEvaluator.ESCALATION_AUDIT.equals(entry.eventType())
						&& PatientLifecycleService.REGISTERED.equals(entry.previousStatus())
						&& PatientLifecycleService.REGISTERED.equals(entry.nextStatus())));
		verify(outboxWriter).write(eq(patientId), eq(SlaEvaluator.ESCALATED_EVENT), anyMap(),
				any(Instant.class));
		verify(alertRepository).create(argThat(alert -> alert.patientId().equals(patientId)
				&& alert.level() == TriageLevel.CRITICO
				&& alert.status() == AlertStatus.PENDIENTE
				&& alert.responsible() == null
				&& alert.waitMinutes().compareTo(BigDecimal.valueOf(6.00)) >= 0));
		verify(metrics).patientEscalated(TriageLevel.CRITICO);
	}

	@Test
	void escalatesWhenTheConditionalUpdateCompletesEmpty() {
		UUID patientId = UUID.randomUUID();
		Instant registeredAt = Instant.parse("2026-03-10T09:54:00Z");
		when(patientStore.findEscalationCandidates()).thenReturn(Flux.just(patient(patientId, registeredAt)));
		when(patientStore.escalate(eq(patientId), any(Instant.class))).thenReturn(Mono.empty());
		when(auditStore.append(any(AuditEntry.class))).thenReturn(Mono.empty());
		when(outboxWriter.write(any(UUID.class), anyString(), anyMap(), any(Instant.class)))
				.thenReturn(Mono.empty());

		StepVerifier.create(evaluator.evaluateOnce())
				.assertNext(count -> assertThat(count).isEqualTo(1))
				.verifyComplete();

		verify(auditStore).append(any(AuditEntry.class));
		verify(outboxWriter).write(eq(patientId), eq(SlaEvaluator.ESCALATED_EVENT), anyMap(),
				any(Instant.class));
		verify(alertRepository).create(any(MedicalAlert.class));
		verify(metrics).patientEscalated(TriageLevel.CRITICO);
	}

	@Test
	void doesNotEscalateBeforeTheSlaBoundary() {
		UUID patientId = UUID.randomUUID();
		when(patientStore.findEscalationCandidates()).thenReturn(Flux.just(patient(patientId,
				Instant.parse("2026-03-10T09:56:00Z"))));

		StepVerifier.create(evaluator.evaluateOnce())
				.assertNext(count -> assertThat(count).isZero())
				.verifyComplete();

		verify(patientStore, never()).escalate(any(UUID.class), any(Instant.class));
		verify(auditStore, never()).append(any(AuditEntry.class));
		verify(outboxWriter, never()).write(any(UUID.class), anyString(), anyMap(), any(Instant.class));
		verify(alertRepository, never()).create(any(MedicalAlert.class));
		verify(metrics, never()).patientEscalated(any(TriageLevel.class));
	}

	@Test
	void doesNotWriteSideEffectsWhenConditionalUpdateLosesRace() {
		UUID patientId = UUID.randomUUID();
		Instant registeredAt = Instant.parse("2026-03-10T09:54:00Z");
		when(patientStore.findEscalationCandidates()).thenReturn(Flux.just(patient(patientId, registeredAt)));
		when(patientStore.escalate(eq(patientId), any(Instant.class))).thenReturn(Mono.just(0));

		StepVerifier.create(evaluator.evaluateOnce())
				.assertNext(count -> assertThat(count).isZero())
				.verifyComplete();

		verify(auditStore, never()).append(any(AuditEntry.class));
		verify(outboxWriter, never()).write(any(UUID.class), anyString(), anyMap(), any(Instant.class));
		verify(alertRepository, never()).create(any(MedicalAlert.class));
		verify(metrics, never()).patientEscalated(any(TriageLevel.class));
	}

	private static PatientSnapshot patient(UUID id, Instant registeredAt) {
		return new PatientSnapshot(id, "Paciente de prueba", 40, 80, 120, 80, 98,
				BigDecimal.valueOf(36.5), 2, 15, "Control", "CRITICO", 5,
				PatientLifecycleService.REGISTERED, false, registeredAt, registeredAt, registeredAt);
	}
}
