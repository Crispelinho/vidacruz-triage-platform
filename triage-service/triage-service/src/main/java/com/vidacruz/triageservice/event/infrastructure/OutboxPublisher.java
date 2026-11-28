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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;


import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
	private final OutboxPublishTracing tracing;

	/** A publisher whose publications are not traced. */
	OutboxPublisher(OutboxEventoRepository repository, KafkaTemplate<String, String> kafkaTemplate, String topic,
			int batchSize, long leaseSeconds, long publishIntervalMs) {
		this(repository, kafkaTemplate, topic, batchSize, leaseSeconds, publishIntervalMs,
				new OutboxPublishTracing(Tracer.NOOP, Propagator.NOOP));
	}

	@Autowired
	public OutboxPublisher(OutboxEventoRepository repository,
			KafkaTemplate<String, String> kafkaTemplate,
			@Value("${app.outbox.topic:${TRIAGE_EVENTS_TOPIC:triaje.eventos}}") String topic,
			@Value("${app.outbox.batch-size:50}") int batchSize,
			@Value("${app.outbox.lease-seconds:30}") long leaseSeconds,
			@Value("${app.outbox.publish-interval-ms:5000}") long publishIntervalMs,
			OutboxPublishTracing tracing) {
		this.tracing = tracing;
		this.repository = repository;
		this.kafkaTemplate = kafkaTemplate;
		this.topic = topic;
		this.batchSize = batchSize;
		this.leaseSeconds = leaseSeconds;
		this.publishIntervalMs = publishIntervalMs;
	}

	// One cycle at a time: delay, publish, then repeat. Ticks cannot queue up while a slow cycle is running.
	Flux<Integer> timerCycles() {
		return Mono.defer(() -> publish()
				.onErrorResume(error -> {
					log.warn("Outbox publication cycle failed", error);
					return Mono.empty();
				}))
				.delaySubscription(Duration.ofMillis(this.publishIntervalMs))
				.repeat();
	}

	private Disposable startTimer() {
		return timerCycles()
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
				// R2DBC @Query UPDATEs complete empty on success; only an explicit 0 is a lost claim.
				.defaultIfEmpty(1)
				.flatMap(claimed -> {
					if (claimed != 1) {
						return Mono.just(0);
					}
					// KafkaTemplate.send() blocks on metadata before returning its future; keep it off the reactor
					// event loop (TST-RXN). The bounded pool is the same non-blocking-boundary pattern as pgvector.
					var send = Mono.defer(() -> Mono.fromFuture(
									() -> kafkaTemplate.send(recordOf(event)), false))
							.subscribeOn(Schedulers.boundedElastic())
							.timeout(Duration.ofSeconds(leaseSeconds));
					return tracing.traced(event.getTraceParent(), topic, send)
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
