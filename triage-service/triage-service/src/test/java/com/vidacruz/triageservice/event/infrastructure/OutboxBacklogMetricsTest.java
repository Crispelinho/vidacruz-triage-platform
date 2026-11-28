package com.vidacruz.triageservice.event.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.scheduling.annotation.Scheduled;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OutboxBacklogMetricsTest {

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

	private final AtomicReference<Mono<OutboxBacklogMetrics.Backlog>> query = new AtomicReference<>();

	private final OutboxBacklogMetrics metrics =
			new OutboxBacklogMetrics(Mono.defer(() -> query.get()), registry);

	@Test
	void readsNoNumberBeforeTheFirstRefresh() {
		assertThat(pending()).isNaN();
	}

	/** A stale value would keep an alert quiet, or firing, on numbers that are no longer known. */
	@Test
	void forgetsTheLastValuesWhenTheDatabaseCannotBeRead() {
		query.set(Mono.just(new OutboxBacklogMetrics.Backlog(4, 1, 0, 12.5)));
		StepVerifier.create(metrics.refresh()).verifyComplete();
		assertThat(pending()).isEqualTo(4);

		query.set(Mono.error(new IllegalStateException("database unavailable")));
		StepVerifier.create(metrics.refresh()).verifyComplete();

		assertThat(pending()).isNaN();
	}

	/** A stalled connection must not leave healthy-looking numbers in place, nor hold the pool for ever. */
	@Test
	void givesUpOnAQueryThatDoesNotAnswer() {
		query.set(Mono.just(new OutboxBacklogMetrics.Backlog(4, 1, 0, 12.5)));
		StepVerifier.create(metrics.refresh()).verifyComplete();
		query.set(Mono.never());

		StepVerifier.withVirtualTime(metrics::refresh)
				.thenAwait(Duration.ofSeconds(10))
				.expectComplete()
				.verify(Duration.ofSeconds(5));

		assertThat(pending()).isNaN();
	}

	/** Spring only waits for a scheduled read, and so never overlaps two, when the method returns its publisher. */
	@Test
	void handsTheScheduledReadToSpringAsAPublisher() {
		assertThat(OutboxBacklogMetrics.class.getDeclaredMethods())
				.filteredOn(method -> method.isAnnotationPresent(Scheduled.class))
				.singleElement()
				.satisfies(method -> assertThat(Publisher.class).isAssignableFrom(method.getReturnType()));
	}

	private double pending() {
		return registry.get("triage.outbox.pending").gauge().value();
	}
}
