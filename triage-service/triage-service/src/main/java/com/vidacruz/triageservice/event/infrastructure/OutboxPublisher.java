package com.vidacruz.triageservice.event.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

	private final OutboxEventoRepository repository;
	private final KafkaSender<String, String> sender;
	private final String topic;
	private final int batchSize;
	private final long leaseSeconds;
	private final String owner = UUID.randomUUID().toString();
	private final AtomicBoolean running = new AtomicBoolean(false);

	public OutboxPublisher(OutboxEventoRepository repository,
			KafkaSender<String, String> sender,
			@Value("${app.outbox.topic:${TRIAGE_EVENTS_TOPIC:triaje.eventos}}") String topic,
			@Value("${app.outbox.batch-size:50}") int batchSize,
			@Value("${app.outbox.lease-seconds:30}") long leaseSeconds) {
		this.repository = repository;
		this.sender = sender;
		this.topic = topic;
		this.batchSize = batchSize;
		this.leaseSeconds = leaseSeconds;
	}

	/**
	 * fixedDelay already prevents overlap within one instance; the guard also covers a slow run that is
	 * still subscribed, and the row lease covers multiple instances.
	 */
	@Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms:5000}")
	public void publishBatch() {
		if (!running.compareAndSet(false, true)) {
			log.debug("Outbox publish skipped: previous run still in progress");
			return;
		}
		publish()
				.doFinally(signal -> running.set(false))
				.subscribe(count -> log.info("Published {} events from outbox", count),
						error -> log.error("Outbox publish run failed", error));
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
						.doOnSuccess(ignored -> log.error("Failed to publish outbox event {}", event.getEventId(), error))
						.thenReturn(0));
	}
}
