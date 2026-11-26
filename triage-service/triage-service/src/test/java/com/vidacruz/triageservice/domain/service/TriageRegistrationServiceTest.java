package com.vidacruz.triageservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.application.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.event.domain.OutboxEvento;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventoRepository;
import com.vidacruz.triageservice.infrastructure.config.SlaProperties;
import com.vidacruz.triageservice.infrastructure.persistence.PatientEntity;
import com.vidacruz.triageservice.infrastructure.persistence.PatientRepository;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TriageRegistrationServiceTest {

	@Mock
	private PatientRepository patientRepository;

	@Mock
	private OutboxEventoRepository outboxRepository;

	private TriageRegistrationService service;

	@BeforeEach
	void setUp() {
		service = new TriageRegistrationService(patientRepository, outboxRepository,
				new TriageClassifier(), new SlaProperties(Map.of()), new ObjectMapper());
		when(patientRepository.save(any(PatientEntity.class)))
				.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
		when(outboxRepository.save(any(OutboxEvento.class)))
				.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
	}

	@Test
	void registersCriticalPatientAndWritesOutboxEvent() {
		PatientRegistrationRequest request = new PatientRegistrationRequest(
				"Paciente de prueba", 40, 130, 180, 110, 97, BigDecimal.valueOf(36.8), 8, 15, "Dolor torácico");

		StepVerifier.create(service.register(request))
				.assertNext(response -> {
					assertThat(response.level()).isEqualTo("CRITICO");
					assertThat(response.slaMinutes()).isEqualTo(5);
					assertThat(response.status()).isEqualTo("REGISTRADO");
					assertThat(response.registeredAt()).isNotNull();
				})
				.verifyComplete();

		verify(patientRepository).save(argThat(entity -> "CRITICO".equals(entity.level())));
		verify(outboxRepository).save(argThat(event -> "paciente_registrado".equals(event.getEventType())));
	}
}
