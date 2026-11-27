package com.vidacruz.triageservice.event.infrastructure;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.vidacruz.triageservice.event.domain.OutboxEvento;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Publishes claimed outbox rows to triaje.eventos with lease protection and failure recording (G-T25). */
@Component
@ConditionalOnProperty(prefix = "app.outbox", name = "publisher-enabled", havingValue = "true")
public class OutboxPublisher {

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
	private static final String TRACEPARENT = "traceparent";

	private final OutboxEventoRepository repository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final String topic;
	private final int batchSize;
	private final long leaseSeconds;
	private final String owner = UUID.randomUUID().toString();
	private final long publishIntervalMs;
	private Disposable timer;

	public OutboxPublisher(OutboxEventoRepository repository,
			KafkaTemplate<String, String> kafkaTemplate,
			@Value("${app.outbox.topic}") String topic,
			@Value("${app.outbox.batch-size}") int batchSize,
			@Value("${app.outbox.lease-seconds}") long leaseSeconds,
			@Value("${app.outbox.publish-interval-ms}") long publishIntervalMs) {
		this.repository = repository;
		this.kafkaTemplate = kafkaTemplate;
		this.topic = topic;
		this.batchSize = batchSize;
		this.leaseSeconds = leaseSeconds;
		this.publishIntervalMs = publishIntervalMs;
	}

	private Disposable startTimer() {
		return Flux.interval(Duration.ofMillis(this.publishIntervalMs))
				.concatMap(ignored -> publish()
						.onErrorResume(error -> {
							log.warn("Outbox publication cycle failed", error);
							return Mono.empty();
						}))
				.subscribe(count -> {
					if (count > 0) {
						log.debug("Published {} outbox event(s)", count);
					}
				}, error -> log.warn("Outbox publisher timer stopped", error));
	}

	@PostConstruct
	void activatesTimer() {
		timer = startTimer();
	}

	@PreDestroy
	void stopTimer() {
		if (timer != null) {
			timer.dispose();
		}
	}

	/** Returns the number of rows published by this run. */
	public Mono<Integer> publish() {
		return repository.findUnpublishedWithoutLease(batchSize)
				.concatMap(this::publishOne)
				.reduce(0, Integer::sum);
	}

	/** The W3C trace context travels with the record, never in the payload (NFR-16). */
	private ProducerRecord<String, String> recordOf(OutboxEvento event) {
		RecordHeaders headers = new RecordHeaders();
		if (event.getTraceParent() != null && !event.getTraceParent().isBlank()) {
			headers.add(TRACEPARENT, event.getTraceParent().getBytes(StandardCharsets.UTF_8));
		}
		return new ProducerRecord<>(topic, null, event.getPacienteId().toString(), event.getPayload(), headers);
	}

	private Mono<Integer> publishOne(OutboxEvento event) {
		OffsetDateTime leaseUntil = OffsetDateTime.ofInstant(
				Instant.now().plusSeconds(leaseSeconds), ZoneOffset.UTC);
		return repository.claimLeaseIfAvailable(event.getEventId(), leaseUntil, owner)
				.flatMap(claimed -> {
					if (claimed != 1) {
						return Mono.just(0);
					}
					return Mono.defer(() -> Mono.fromFuture(
									() -> kafkaTemplate.send(recordOf(event)), false))
							.timeout(Duration.ofSeconds(leaseSeconds))
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
