package com.vidacruz.triageservice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vidacruz.triageservice.domain.model.Patient;
import com.vidacruz.triageservice.domain.model.TriageLevel;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class R2dbcPatientStoreTest {

	private static final UUID ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-03-10T10:00:00Z");

	@Mock
	private PatientRepository repository;

	@InjectMocks
	private R2dbcPatientStore store;

	@Test
	void conditionalUpdateThatCompletesEmptyCountsAsWon() {
		// R2DBC @Query UPDATEs complete empty on success in this stack.
		when(repository.escalate(eq(ID), any(Instant.class))).thenReturn(Mono.empty());
		when(repository.changeStatus(eq(ID), eq("REGISTRADO"), eq("ALTA"), any(Instant.class)))
				.thenReturn(Mono.empty());

		StepVerifier.create(store.markEscalated(ID, NOW)).expectNext(true).verifyComplete();
		StepVerifier.create(store.changeStatus(ID, "REGISTRADO", "ALTA", NOW)).expectNext(true).verifyComplete();
	}

	@Test
	void onlyAnUpdateOfExactlyOneRowWins() {
		when(repository.escalate(eq(ID), any(Instant.class))).thenReturn(Mono.just(1), Mono.just(0));

		StepVerifier.create(store.markEscalated(ID, NOW)).expectNext(true).verifyComplete();
		StepVerifier.create(store.markEscalated(ID, NOW)).expectNext(false).verifyComplete();
	}

	@Test
	void mapsEveryColumnBothWays() {
		Patient patient = new Patient(ID, "Paciente de prueba", 40, 80, 120, 80, 98, new BigDecimal("36.5"), 2, 15,
				"Control", TriageLevel.URGENTE, 10, "REGISTRADO", true, NOW.minusSeconds(600), NOW.minusSeconds(60),
				NOW.minusSeconds(300));

		assertThat(R2dbcPatientStore.toDomain(R2dbcPatientStore.toEntity(patient))).isEqualTo(patient);
	}

	@Test
	void addInsertsTheMappedRow() {
		Patient patient = new Patient(ID, "Paciente de prueba", 40, 80, 120, 80, 98, new BigDecimal("36.5"), 2, 15,
				"Control", TriageLevel.CRITICO, 5, "REGISTRADO", false, NOW, NOW, NOW);
		when(repository.save(any(PatientEntity.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

		StepVerifier.create(store.add(patient)).verifyComplete();

		verify(repository).save(argThat(entity -> entity.isNew() && "CRITICO".equals(entity.level())
				&& Boolean.FALSE.equals(entity.escalated())));
	}
}
