package com.vidacruz.triageservice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vidacruz.triageservice.application.dto.PatientRegistrationResponse;
import com.vidacruz.triageservice.domain.model.RegistrationResult;
import com.vidacruz.triageservice.domain.model.TriageLevel;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class R2dbcIdempotencyStoreTest {

	/** Same settings as the Spring Boot ObjectMapper: JavaTimeModule and ISO-8601 dates. */
	private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	@Mock
	private IdempotencyKeyRepository repository;

	private R2dbcIdempotencyStore store;

	@BeforeEach
	void setUp() {
		store = new R2dbcIdempotencyStore(repository, mapper);
	}

	@Test
	void replaysABodyStoredByEarlierVersionsAsTheApiResponse() throws Exception {
		UUID id = UUID.randomUUID();
		Instant registeredAt = Instant.parse("2026-03-10T09:59:30.123456Z");
		String legacyBody = mapper.writeValueAsString(
				new PatientRegistrationResponse(id, "CRITICO", 5, "REGISTRADO", registeredAt));
		when(repository.findById("key")).thenReturn(Mono.just(
				new IdempotencyKeyEntity("key", "hash", id, 201, legacyBody, registeredAt, registeredAt)));

		StepVerifier.create(store.find("key"))
				.assertNext(record -> {
					assertThat(record.requestHash()).isEqualTo("hash");
					assertThat(record.response())
							.isEqualTo(new RegistrationResult(id, TriageLevel.CRITICO, 5, "REGISTRADO", registeredAt));
				})
				.verifyComplete();
	}

	@Test
	void storesTheApiWireFormatWithStatus201() throws Exception {
		RegistrationResult result = new RegistrationResult(UUID.randomUUID(), TriageLevel.URGENTE, 10, "REGISTRADO",
				Instant.parse("2026-03-10T10:00:00Z"));
		String expected = mapper.writeValueAsString(new PatientRegistrationResponse(result.id(), "URGENTE", 10,
				"REGISTRADO", result.registeredAt()));
		when(repository.complete(eq("key"), eq(result.id()), eq(201), anyString())).thenReturn(Mono.just(1));

		StepVerifier.create(store.complete("key", result)).verifyComplete();

		verify(repository).complete(eq("key"), eq(result.id()), eq(201), argThat(body -> body.equals(expected)));
	}

	@Test
	void reservationWinsOnlyWhenOneRowWasInserted() {
		Instant now = Instant.now();
		when(repository.insertPending("a", "h", now, now)).thenReturn(Mono.just(1));
		when(repository.insertPending("b", "h", now, now)).thenReturn(Mono.just(0));

		StepVerifier.create(store.reserve("a", "h", now, now)).expectNext(true).verifyComplete();
		StepVerifier.create(store.reserve("b", "h", now, now)).expectNext(false).verifyComplete();
	}

	@Test
	void unreadableStoredBodyIsAServerError() {
		when(repository.findById("broken")).thenReturn(Mono.just(
				new IdempotencyKeyEntity("broken", "hash", UUID.randomUUID(), 201, "not json", Instant.now(),
						Instant.now())));

		StepVerifier.create(store.find("broken"))
				.expectErrorSatisfies(error -> assertThat(error)
						.isInstanceOf(IllegalStateException.class)
						.hasMessage("Stored idempotency response is not readable"))
				.verify();
	}
}
