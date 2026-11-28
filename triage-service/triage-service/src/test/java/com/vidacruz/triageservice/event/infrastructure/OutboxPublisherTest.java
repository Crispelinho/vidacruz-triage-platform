package com.vidacruz.triageservice.event.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import com.vidacruz.triageservice.event.infrastructure.OutboxEvento;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.scheduler.VirtualTimeScheduler;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

	private static final String TRACEPARENT =
			"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

	@Mock
	private OutboxEventoRepository repository;

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;

	@Test
	void publishesClaimedRowWithPatientKeyTraceparentHeaderAndMarksItPublished() {
		UUID patientId = UUID.randomUUID();
		OutboxEvento event = new OutboxEvento(UUID.randomUUID(), "paciente_registrado", "{}", patientId,
				1, OffsetDateTime.now(), UUID.randomUUID());
		event.setTraceParent(TRACEPARENT);
		when(repository.findUnpublishedWithoutLease(10)).thenReturn(Flux.just(event));
		when(repository.claimLeaseIfAvailable(any(UUID.class), any(OffsetDateTime.class), anyString()))
				.thenReturn(Mono.just(1));
		when(kafkaTemplate.send(any(ProducerRecord.class)))
				.thenReturn(CompletableFuture.completedFuture(null));
		when(repository.markPublished(any(UUID.class), any(OffsetDateTime.class))).thenReturn(Mono.empty());

		OutboxPublisher publisher = new OutboxPublisher(repository, kafkaTemplate, "triaje.eventos", 10, 30, 60000);

		StepVerifier.create(publisher.publish())
				.expectNext(1)
				.verifyComplete();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate).send(captor.capture());
		ProducerRecord<String, String> record = captor.getValue();
		assertThat(record.topic()).isEqualTo("triaje.eventos");
		assertThat(record.key()).isEqualTo(patientId.toString());
		assertThat(record.value()).isEqualTo("{}");
		assertThat(record.headers().lastHeader("traceparent")).isNotNull();
		assertThat(new String(record.headers().lastHeader("traceparent").value(), StandardCharsets.UTF_8))
				.isEqualTo(TRACEPARENT);
		verify(repository).markPublished(org.mockito.ArgumentMatchers.eq(event.getEventId()),
				any(OffsetDateTime.class));
		verify(repository, never()).recordFailure(any(UUID.class), anyString(), any(Boolean.class));
	}

	@Test
	void omitsTheTraceparentHeaderForEventsWrittenOutsideATrace() {
		UUID patientId = UUID.randomUUID();
		OutboxEvento event = new OutboxEvento(UUID.randomUUID(), "paciente_registrado", "{}", patientId,
				1, OffsetDateTime.now(), UUID.randomUUID());
		when(repository.findUnpublishedWithoutLease(10)).thenReturn(Flux.just(event));
		when(repository.claimLeaseIfAvailable(any(UUID.class), any(OffsetDateTime.class), anyString()))
				.thenReturn(Mono.just(1));
		when(kafkaTemplate.send(any(ProducerRecord.class)))
				.thenReturn(CompletableFuture.completedFuture(null));
		when(repository.markPublished(any(UUID.class), any(OffsetDateTime.class))).thenReturn(Mono.empty());

		OutboxPublisher publisher = new OutboxPublisher(repository, kafkaTemplate, "triaje.eventos", 10, 30, 60000);

		StepVerifier.create(publisher.publish())
				.expectNext(1)
				.verifyComplete();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate).send(captor.capture());
		assertThat(captor.getValue().headers().lastHeader("traceparent")).isNull();
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

		verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
	}

	@Test
	void publishesClaimedRowWhenTheLeaseUpdateCompletesEmpty() {
		UUID patientId = UUID.randomUUID();
		OutboxEvento event = new OutboxEvento(UUID.randomUUID(), "paciente_registrado", "{}", patientId,
				1, OffsetDateTime.now(), UUID.randomUUID());
		when(repository.findUnpublishedWithoutLease(10)).thenReturn(Flux.just(event));
		when(repository.claimLeaseIfAvailable(any(UUID.class), any(OffsetDateTime.class), anyString()))
				.thenReturn(Mono.empty());
		when(kafkaTemplate.send(any(ProducerRecord.class)))
				.thenReturn(CompletableFuture.completedFuture(null));
		when(repository.markPublished(any(UUID.class), any(OffsetDateTime.class))).thenReturn(Mono.empty());

		OutboxPublisher publisher = new OutboxPublisher(repository, kafkaTemplate, "triaje.eventos", 10, 30, 60000);

		StepVerifier.create(publisher.publish())
				.expectNext(1)
				.verifyComplete();

		verify(kafkaTemplate).send(any(ProducerRecord.class));
		verify(repository).markPublished(org.mockito.ArgumentMatchers.eq(event.getEventId()),
				any(OffsetDateTime.class));
		verify(repository, never()).recordFailure(any(UUID.class), anyString(), any(Boolean.class));
	}

	@Test
	void timerStartsTheNextCycleAfterASlowOneInsteadOfQueuingTicks() {
		int batchSize = 10;
		long intervalMs = 20;
		long publishMs = 40;
		OutboxEvento event = new OutboxEvento(UUID.randomUUID(), "paciente_registrado", "{}", UUID.randomUUID(),
				1, OffsetDateTime.now(), UUID.randomUUID());
		List<Long> cycleStarts = new ArrayList<>();
		VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
		when(repository.findUnpublishedWithoutLease(batchSize)).thenAnswer(invocation -> Flux.defer(() -> {
			cycleStarts.add(scheduler.now(TimeUnit.MILLISECONDS));
			return Mono.delay(Duration.ofMillis(publishMs)).thenMany(Flux.just(event));
		}));
		when(repository.claimLeaseIfAvailable(any(UUID.class), any(OffsetDateTime.class), anyString()))
				.thenReturn(Mono.just(1));
		when(kafkaTemplate.send(any(ProducerRecord.class)))
				.thenReturn(CompletableFuture.completedFuture(null));
		when(repository.markPublished(any(UUID.class), any(OffsetDateTime.class))).thenReturn(Mono.empty());

		OutboxPublisher publisher = new OutboxPublisher(repository, kafkaTemplate, "triaje.eventos",
				batchSize, 30, intervalMs);

		StepVerifier.withVirtualTime(() -> publisher.timerCycles().take(2), () -> scheduler, Long.MAX_VALUE)
				.thenAwait(Duration.ofMillis(intervalMs + publishMs))
				.expectNext(1)
				.thenAwait(Duration.ofMillis(intervalMs + publishMs))
				.expectNext(1)
				.verifyComplete();

		assertThat(cycleStarts).hasSize(2);
		assertThat(cycleStarts.get(1) - cycleStarts.get(0))
				.as("the next cycle is delayed until after the slow one finishes")
				.isGreaterThanOrEqualTo(intervalMs + publishMs);
	}
}
