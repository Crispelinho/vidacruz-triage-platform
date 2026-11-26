package com.vidacruz.triageservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.vidacruz.triageservice.application.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.event.domain.OutboxEvento;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventWriter;
import com.vidacruz.triageservice.infrastructure.config.SlaProperties;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryEntity;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryRepository;
import com.vidacruz.triageservice.infrastructure.persistence.PatientEntity;
import com.vidacruz.triageservice.infrastructure.persistence.PatientRepository;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TriageRegistrationServiceTest {

	@Mock
	private PatientRepository patientRepository;

	@Mock
	private AuditEntryRepository auditRepository;

	@Mock
	private OutboxEventWriter eventWriter;

	@Mock
	private TransactionalOperator transactionalOperator;

	private TriageRegistrationService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		service = new TriageRegistrationService(patientRepository, auditRepository, eventWriter,
				new TriageClassifier(), new SlaProperties(Map.of()), transactionalOperator);
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
	void registersCriticalPatientWritesAuditAndOutboxEvent() {
		PatientRegistrationRequest request = new PatientRegistrationRequest(
				"Paciente de prueba", 40, 130, 180, 110, 97, BigDecimal.valueOf(36.8), 8, 15, "Dolor torácico");

		StepVerifier.create(service.register(request))
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
	}
}
