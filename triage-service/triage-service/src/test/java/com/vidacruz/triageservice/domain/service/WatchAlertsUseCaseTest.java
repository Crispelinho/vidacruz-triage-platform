package com.vidacruz.triageservice.domain.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vidacruz.triageservice.domain.model.AlertStatus;
import com.vidacruz.triageservice.domain.model.MedicalAlert;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.TriageLevel;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class WatchAlertsUseCaseTest {

	@Mock
	private MedicalAlertRepository repository;

	@Test
	void subscribesToChangesBeforeTheSnapshotToAvoidLosingAnAlert() {
		var snapshot = alert(UUID.randomUUID());
		var change = alert(UUID.randomUUID());
		when(repository.findActive()).thenReturn(Flux.just(snapshot));
		when(repository.changes()).thenReturn(Flux.just(change));

		StepVerifier.create(new WatchAlertsUseCase(repository).watch())
				.expectNext(change)
				.expectNext(snapshot)
				.verifyComplete();

		InOrder order = inOrder(repository);
		order.verify(repository).changes();
		order.verify(repository).findActive();
	}

	private static MedicalAlert alert(UUID id) {
		Instant now = Instant.parse("2026-11-26T10:00:00Z");
		return new MedicalAlert(id, UUID.randomUUID(), TriageLevel.CRITICO, 5,
				now.minusSeconds(300), now, BigDecimal.valueOf(5), AlertStatus.PENDIENTE,
				null, null, null, now);
	}
}
