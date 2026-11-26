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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.vidacruz.triageservice.event.domain.OutboxEvento;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventWriter;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryEntity;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryRepository;
import com.vidacruz.triageservice.infrastructure.persistence.PatientEntity;
import com.vidacruz.triageservice.infrastructure.persistence.PatientRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SlaEvaluatorTest {

	@Mock
	private PatientRepository patientRepository;

	@Mock
	private AuditEntryRepository auditRepository;

	@Mock
	private OutboxEventWriter eventWriter;

	@Mock
	private TransactionalOperator transactionalOperator;

	private SlaEvaluator evaluator;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		evaluator = new SlaEvaluator(patientRepository, auditRepository, eventWriter, transactionalOperator);
		lenient().when(transactionalOperator.transactional(any(Mono.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void escalatesOverduePatientOnce() {
		UUID patientId = UUID.randomUUID();
		Instant registeredAt = Instant.now().minus(6, ChronoUnit.MINUTES);
		when(patientRepository.findEscalationCandidates()).thenReturn(Flux.just(patient(patientId, registeredAt)));
		when(patientRepository.escalate(eq(patientId), any(Instant.class))).thenReturn(Mono.just(1));
		when(auditRepository.save(any(AuditEntryEntity.class)))
				.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
		when(eventWriter.write(any(UUID.class), anyString(), anyMap(), any(Instant.class)))
				.thenReturn(Mono.just(new OutboxEvento(UUID.randomUUID(), "paciente_escalado", "{}",
						patientId, 2, OffsetDateTime.now(), UUID.randomUUID())));

		StepVerifier.create(evaluator.evaluateOnce())
				.assertNext(count -> assertThat(count).isEqualTo(1))
				.verifyComplete();

		verify(auditRepository).save(org.mockito.ArgumentMatchers.argThat(entry ->
				SlaEvaluator.ESCALATION_AUDIT.equals(entry.eventType())
						&& PatientLifecycleService.REGISTERED.equals(entry.previousStatus())
						&& PatientLifecycleService.REGISTERED.equals(entry.nextStatus())));
		verify(eventWriter).write(eq(patientId), eq(SlaEvaluator.ESCALATED_EVENT), anyMap(),
				any(Instant.class));
	}

	@Test
	void doesNotEscalateBeforeTheSlaBoundary() {
		UUID patientId = UUID.randomUUID();
		when(patientRepository.findEscalationCandidates()).thenReturn(Flux.just(patient(patientId,
				Instant.now().minus(4, ChronoUnit.MINUTES))));

		StepVerifier.create(evaluator.evaluateOnce())
				.assertNext(count -> assertThat(count).isZero())
				.verifyComplete();

		verify(patientRepository, never()).escalate(eq(patientId), any(Instant.class));
	}

	@Test
	void secondEvaluationDoesNothingWhenConditionalUpdateWinsNoRow() {
		UUID patientId = UUID.randomUUID();
		when(patientRepository.findEscalationCandidates()).thenReturn(Flux.just(patient(patientId,
				Instant.now().minus(6, ChronoUnit.MINUTES))));
		when(patientRepository.escalate(eq(patientId), any(Instant.class))).thenReturn(Mono.just(0));

		StepVerifier.create(evaluator.evaluateOnce())
				.assertNext(count -> assertThat(count).isZero())
				.verifyComplete();

		verify(auditRepository, never()).save(any(AuditEntryEntity.class));
		verify(eventWriter, never()).write(any(UUID.class), anyString(), anyMap(), any(Instant.class));
	}

	private static PatientEntity patient(UUID id, Instant registeredAt) {
		return new PatientEntity(id, "Paciente de prueba", 40, 80, 120, 80, 98,
				BigDecimal.valueOf(36.5), 2, 15, "Control", "CRITICO", 5,
				PatientLifecycleService.REGISTERED, false, registeredAt, registeredAt);
	}
}
