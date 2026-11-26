package com.vidacruz.triageservice.event.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vidacruz.triageservice.event.domain.OutboxEvento;

import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

/** Publishes claimed outbox rows to triaje.eventos with lease protection and failure recording. */
@Component
@ConditionalOnProperty(prefix = "app.outbox", name = "publisher-enabled", havingValue = "true")
public class OutboxPublisher {

	private final OutboxEventoRepository repository;
	private final KafkaSender<String, String> sender;
	private final String topic;
	private final int batchSize;
	private final long leaseSeconds;
	private final String owner = UUID.randomUUID().toString();

	public OutboxPublisher(OutboxEventoRepository repository,
			KafkaSender<String, String> sender,
			@Value("${app.outbox.topic}") String topic,
			@Value("${app.outbox.batch-size}") int batchSize,
			@Value("${app.outbox.lease-seconds}") long leaseSeconds) {
		this.repository = repository;
		this.sender = sender;
		this.topic = topic;
		this.batchSize = batchSize;
		this.leaseSeconds = leaseSeconds;
	}

	@Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms}")
	public void publishBatch() {
		publish().block(Duration.ofSeconds(leaseSeconds));
	}

	/** Returns the number of rows published by this run. */
	public Mono<Integer> publish() {
		return repository.findUnpublishedWithoutLease(batchSize)
				.concatMap(this::publishOne)
				.reduce(0, Integer::sum);
	}

	private Mono<Integer> publishOne(OutboxEvento event) {
		OffsetDateTime leaseUntil = OffsetDateTime.ofInstant(
				Instant.now().plusSeconds(leaseSeconds), ZoneOffset.UTC);
		return repository.claimLeaseIfAvailable(event.getEventId(), leaseUntil, owner)
				.flatMap(claimed -> {
					if (claimed != 1) {
						return Mono.just(0);
					}
					ProducerRecord<String, String> record = new ProducerRecord<>(
							topic, event.getPacienteId().toString(), event.getPayload());
					return sender.send(Mono.just(SenderRecord.create(record, event.getEventId())))
							.then(repository.markPublished(event.getEventId(),
									OffsetDateTime.now(ZoneOffset.UTC)))
							.thenReturn(1);
				})
				.onErrorResume(error -> repository.recordFailure(
						event.getEventId(),
						error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
						false)
						.thenReturn(0));
	}
}
