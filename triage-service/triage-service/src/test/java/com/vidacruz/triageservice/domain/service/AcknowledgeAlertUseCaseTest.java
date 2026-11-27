package com.vidacruz.triageservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vidacruz.triageservice.domain.exception.AlertConflictException;
import com.vidacruz.triageservice.domain.exception.AlertNotFoundException;
import com.vidacruz.triageservice.domain.exception.InvalidAlertRequestException;
import com.vidacruz.triageservice.domain.model.AlertStatus;
import com.vidacruz.triageservice.domain.model.MedicalAlert;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.model.TriageMetrics;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AcknowledgeAlertUseCaseTest {

	private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

	@Mock
	private MedicalAlertRepository repository;

	@Mock
	private TriageMetrics metrics;

	@Test
	void takesAPendingAlertAndReturnsTheUpdatedRow() {
		UUID id = UUID.randomUUID();
		when(repository.acknowledge(id, "Dra. Pérez", NOW)).thenReturn(Mono.just(true));
		when(repository.findById(id)).thenReturn(Mono.just(alert(id, AlertStatus.RECONOCIDA, "Dra. Pérez")));

		StepVerifier.create(useCase().acknowledge(id, "  Dra. Pérez  ", NOW))
				.assertNext(alert -> {
					assertThat(alert.status()).isEqualTo(AlertStatus.RECONOCIDA);
					assertThat(alert.responsible()).isEqualTo("Dra. Pérez");
				})
				.verifyComplete();

		verify(metrics).alertAcknowledged(TriageLevel.CRITICO);
	}

	@Test
	void aResolvedAlertCannotBeAcknowledged() {
		UUID id = UUID.randomUUID();
		when(repository.acknowledge(eq(id), any(String.class), eq(NOW))).thenReturn(Mono.just(false));
		when(repository.findById(id)).thenReturn(Mono.just(alert(id, AlertStatus.RESUELTA, null)));

		StepVerifier.create(useCase().acknowledge(id, "Dra. Pérez", NOW))
				.expectErrorSatisfies(error -> assertThat(error)
						.isInstanceOf(AlertConflictException.class)
						.hasMessage("La alerta ya fue resuelta: el paciente fue atendido o dado de alta"))
				.verify();
	}

	@Test
	void anUnknownAlertIsNotFound() {
		UUID id = UUID.randomUUID();
		when(repository.acknowledge(eq(id), any(String.class), eq(NOW))).thenReturn(Mono.just(false));
		when(repository.findById(id)).thenReturn(Mono.empty());

		StepVerifier.create(useCase().acknowledge(id, "Dra. Pérez", NOW))
				.expectError(AlertNotFoundException.class)
				.verify();
	}

	@Test
	void rejectsAnEmptyResponsibleBeforeQuerying() {
		assertThatThrownBy(() -> useCase().acknowledge(UUID.randomUUID(), "  ", NOW))
				.isInstanceOf(InvalidAlertRequestException.class)
				.satisfies(error -> assertThat(((InvalidAlertRequestException) error).field()).isEqualTo("responsable"));
	}

	private AcknowledgeAlertUseCase useCase() {
		return new AcknowledgeAlertUseCase(repository, metrics);
	}

	private static MedicalAlert alert(UUID id, AlertStatus status, String responsible) {
		return new MedicalAlert(id, UUID.randomUUID(), TriageLevel.CRITICO, 5,
				NOW.minusSeconds(300), NOW, BigDecimal.valueOf(5), status, responsible,
				status == AlertStatus.PENDIENTE ? null : NOW, null, NOW);
	}
}
