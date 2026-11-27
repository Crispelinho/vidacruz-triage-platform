package com.vidacruz.triageservice.event.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import com.vidacruz.triageservice.event.domain.OutboxEvento;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

	@Mock
	private OutboxEventoRepository repository;

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;

	@Test
	void publishesClaimedRowWithPatientKeyAndMarksItPublished() {
		UUID patientId = UUID.randomUUID();
		OutboxEvento event = new OutboxEvento(UUID.randomUUID(), "paciente_registrado", "{}", patientId,
				1, OffsetDateTime.now(), UUID.randomUUID());
		when(repository.findUnpublishedWithoutLease(10)).thenReturn(Flux.just(event));
		when(repository.claimLeaseIfAvailable(any(UUID.class), any(OffsetDateTime.class), anyString()))
				.thenReturn(Mono.just(1));
		when(kafkaTemplate.send(anyString(), anyString(), anyString()))
				.thenReturn(CompletableFuture.completedFuture(null));
		when(repository.markPublished(any(UUID.class), any(OffsetDateTime.class))).thenReturn(Mono.empty());

		OutboxPublisher publisher = new OutboxPublisher(repository, kafkaTemplate, "triaje.eventos", 10, 30, 60000);

		StepVerifier.create(publisher.publish())
				.expectNext(1)
				.verifyComplete();

		verify(kafkaTemplate).send("triaje.eventos", patientId.toString(), "{}");
		verify(repository).markPublished(org.mockito.ArgumentMatchers.eq(event.getEventId()),
				any(OffsetDateTime.class));
		verify(repository, never()).recordFailure(any(UUID.class), anyString(), any(Boolean.class));
	}

	@Test
	void doesNotSendWhenAnotherOwnerHoldsTheLease() {
		UUID patientId = UUID.randomUUID();
		OutboxEvento event = new OutboxEvento(UUID.randomUUID(), "paciente_registrado", "{}", patientId,
				1, OffsetDateTime.now(), UUID.randomUUID());
		when(repository.findUnpublishedWithoutLease(10)).thenReturn(Flux.just(event));
		when(repository.claimLeaseIfAvailable(any(UUID.class), any(OffsetDateTime.class), anyString()))
				.thenReturn(Mono.just(0));

		OutboxPublisher publisher = new OutboxPublisher(repository, kafkaTemplate, "triaje.eventos", 10, 30, 60000);

		StepVerifier.create(publisher.publish())
				.expectNext(0)
				.verifyComplete();

		verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
	}
}
