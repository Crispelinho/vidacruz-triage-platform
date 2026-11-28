package com.vidacruz.triageservice.event.infrastructure;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

/**
 * Outbox backlog and failure gauges (NFR-07). A scrape never touches the database: a scheduled, non-blocking query
 * refreshes the values the gauges read. Until the first refresh, and after a failed one, they read NaN so that an
 * alert is never evaluated on numbers that are not known.
 */
@Component
public class OutboxBacklogMetrics {

	private static final Logger log = LoggerFactory.getLogger(OutboxBacklogMetrics.class);

	/* Rows flagged for review are still retried by the publisher, so they stay part of the pending backlog. */
	private static final String QUERY = """
			SELECT COUNT(*) AS pending,
				COUNT(*) FILTER (WHERE intentos > 0) AS failing,
				COUNT(*) FILTER (WHERE requiere_revision) AS requires_review,
				CAST(COALESCE(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - MIN(created_at))), 0) AS double precision)
					AS oldest_age
			FROM outbox_evento
			WHERE publicado_en IS NULL
			""";

	record Backlog(long pending, long failing, long requiresReview, double oldestPendingAgeSeconds) {
	}

	private final Mono<Backlog> query;

	private final AtomicReference<Backlog> latest = new AtomicReference<>();

	@Autowired
	public OutboxBacklogMetrics(DatabaseClient db, MeterRegistry registry) {
		this(db.sql(QUERY)
				.map((row, metadata) -> new Backlog(row.get("pending", Long.class), row.get("failing", Long.class),
						row.get("requires_review", Long.class), row.get("oldest_age", Double.class)))
				.one(), registry);
	}

	OutboxBacklogMetrics(Mono<Backlog> query, MeterRegistry registry) {
		this.query = query;
		gauge(registry, "triage.outbox.pending", "Outbox events not yet published", null, Backlog::pending);
		gauge(registry, "triage.outbox.failing", "Unpublished outbox events with at least one failed attempt", null,
				Backlog::failing);
		gauge(registry, "triage.outbox.requires.review", "Unpublished outbox events flagged for an operator", null,
				Backlog::requiresReview);
		gauge(registry, "triage.outbox.oldest.pending.age", "Age of the oldest unpublished outbox event", "seconds",
				Backlog::oldestPendingAgeSeconds);
	}

	/** Reads the backlog once. A failure is logged and clears the values; the signal itself completes. */
	public Mono<Void> refresh() {
		return query.doOnNext(latest::set)
				.then()
				.onErrorResume(error -> {
					latest.set(null);
					log.warn("Could not read the outbox backlog", error);
					return Mono.empty();
				});
	}

	@Scheduled(fixedDelayString = "${app.outbox.metrics-interval-ms:15000}")
	void scheduledRefresh() {
		refresh().subscribe();
	}

	private void gauge(MeterRegistry registry, String name, String description, String unit,
			ToDoubleFunction<Backlog> value) {
		Gauge.builder(name, latest, reference -> {
			Backlog backlog = reference.get();
			return backlog == null ? Double.NaN : value.applyAsDouble(backlog);
		}).description(description).baseUnit(unit).register(registry);
	}
}
