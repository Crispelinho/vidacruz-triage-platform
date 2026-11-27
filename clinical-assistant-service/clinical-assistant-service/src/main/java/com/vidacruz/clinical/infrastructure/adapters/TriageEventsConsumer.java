package com.vidacruz.clinical.infrastructure.adapters;

import com.vidacruz.clinical.domain.services.ProjectTriageEventUseCase;
import com.vidacruz.clinical.infrastructure.config.TriageEventsProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.MicrometerConsumerListener;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverPartition;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.receiver.observation.KafkaReceiverObservation;
import reactor.kafka.receiver.observation.KafkaRecordReceiverContext;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds the projection of the emergency room from triaje.eventos (RF-04B), without calling the triage service.
 * Partitions are applied in order, offsets are acknowledged after the projection succeeds, unreadable messages are
 * skipped, and an empty projection is rebuilt from the beginning of the topic.
 */
@Component
@ConditionalOnProperty(prefix = "adapters.kafka.patient-events", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class TriageEventsConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TriageEventsConsumer.class);
    private static final Duration FIRST_RESTART_PAUSE = Duration.ofSeconds(1);
    private static final Duration MAX_RESTART_PAUSE = Duration.ofSeconds(30);
    private static final Duration COMMIT_INTERVAL = Duration.ofSeconds(1);
    /** Receiver name used in the span of a projected event (NFR-16). */
    private static final String RECEIVER_ID = "triage-events-projection";

    private final ProjectTriageEventUseCase projectTriageEvent;
    private final TriageEventsProperties properties;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;
    private final UrgencyEventsMetrics metrics;
    private final AtomicReference<Disposable> subscription = new AtomicReference<>();

    public TriageEventsConsumer(ProjectTriageEventUseCase projectTriageEvent, TriageEventsProperties properties,
                                MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
        this.projectTriageEvent = projectTriageEvent;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
        this.metrics = new UrgencyEventsMetrics(meterRegistry);
    }

    @Override
    public void start() {
        subscription.set(consume()
                .retryWhen(Retry.backoff(Long.MAX_VALUE, FIRST_RESTART_PAUSE)
                        .maxBackoff(MAX_RESTART_PAUSE)
                        .doBeforeRetry(signal -> log.atWarn()
                                .addKeyValue("attempt", signal.totalRetries() + 1)
                                .setCause(signal.failure())
                                .log("Triage events consumer failed; starting again")))
                .subscribe());
    }

    @Override
    public void stop() {
        var running = subscription.getAndSet(null);
        if (running != null) {
            running.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        var running = subscription.get();
        return running != null && !running.isDisposed();
    }

    Flux<ReceiverOffset> consume() {
        return projectTriageEvent.needsRebuild()
                // Creating the Kafka consumer does blocking work (DNS, metadata): never on a Netty event loop.
                .publishOn(Schedulers.boundedElastic())
                .flatMapMany(rebuild -> KafkaReceiver.create(options(rebuild)).receive())
                .groupBy(received -> received.receiverOffset().topicPartition())
                .flatMap(partition -> partition.concatMap(this::handle));
    }

    /** Handles one event inside an observation that continues the trace started by the triage request (NFR-16). */
    Mono<ReceiverOffset> handle(ReceiverRecord<String, String> received) {
        return Mono.defer(() -> {
            var observation = KafkaReceiverObservation.RECEIVER_OBSERVATION.start(null,
                    KafkaReceiverObservation.DefaultKafkaReceiverObservationConvention.INSTANCE,
                    () -> new KafkaRecordReceiverContext(received, RECEIVER_ID, properties.bootstrapServers()),
                    observationRegistry);
            return project(received)
                    .doOnError(observation::error)
                    .doFinally(signal -> observation.stop())
                    .contextWrite(context -> context.put(ObservationThreadLocalAccessor.KEY, observation));
        });
    }

    private Mono<ReceiverOffset> project(ReceiverRecord<String, String> received) {
        var offset = received.receiverOffset();
        return Mono.fromCallable(() -> TriageEventParser.parse(received.value(), received.key()))
                .flatMap(Mono::justOrEmpty)
                .flatMap(projectTriageEvent::project)
                .doOnNext(metrics::projected)
                .switchIfEmpty(Mono.fromRunnable(() -> skip(offset, "event type not projected")))
                .onErrorResume(TriageEventsConsumer::isUnreadable, error -> Mono.fromRunnable(
                        () -> skip(offset, error.getMessage())))
                .then(Mono.fromRunnable(offset::acknowledge))
                .thenReturn(offset);
    }

    ReceiverOptions<String, String> options(boolean rebuild) {
        var firstAssignment = new AtomicBoolean(true);
        return ReceiverOptions.<String, String>create(Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers(),
                        ConsumerConfig.GROUP_ID_CONFIG, properties.groupId(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))
                .commitInterval(COMMIT_INTERVAL)
                .consumerListener(new MicrometerConsumerListener(meterRegistry))
                .subscription(List.of(properties.topic()))
                .addAssignListener(partitions -> {
                    if (rebuild && firstAssignment.getAndSet(false)) {
                        rebuildFromTheBeginning(partitions);
                    }
                });
    }

    private void rebuildFromTheBeginning(Iterable<ReceiverPartition> partitions) {
        partitions.forEach(ReceiverPartition::seekToBeginning);
        log.atWarn().addKeyValue("topic", properties.topic())
                .log("Urgency projection is empty; rebuilding it from the beginning of the topic");
        projectTriageEvent.rebuildStarted().subscribe(
                null, error -> log.atError().setCause(error).log("Could not mark the projection as rebuilt"));
    }

    private static boolean isUnreadable(Throwable error) {
        return error instanceof IllegalArgumentException;
    }

    private void skip(ReceiverOffset offset, String reason) {
        metrics.skipped();
        log.atWarn()
                .addKeyValue("partition", offset.topicPartition().partition())
                .addKeyValue("offset", offset.offset())
                .addKeyValue("reason", reason)
                .log("Triage event skipped");
    }
}
