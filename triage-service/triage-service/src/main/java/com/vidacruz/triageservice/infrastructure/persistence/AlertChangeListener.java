package com.vidacruz.triageservice.infrastructure.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.vidacruz.triageservice.domain.model.MedicalAlert;

import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

/**
 * RF-06 change feed. PostgreSQL NOTIFY is emitted by a trigger after the alert row is committed, so no uncommitted
 * alert is ever streamed. This listener owns a dedicated connection outside the application pool: a LISTEN session
 * cannot be returned to a pool. If the connection drops, it reconnects with backoff and replays the changes missed
 * while it was away.
 */
@Component
public class AlertChangeListener implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(AlertChangeListener.class);
	private static final String CHANNEL = "medical_alert";
	private static final Duration FIRST_BACKOFF = Duration.ofSeconds(1);
	private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);
	private static final Duration CATCH_UP_MARGIN = Duration.ofSeconds(5);

	private final DatabaseClient client;
	private final ConnectionFactory connectionFactory;
	private final Sinks.Many<MedicalAlert> sink = Sinks.many().multicast().directBestEffort();
	private final AtomicReference<Instant> lostAt = new AtomicReference<>();

	private Disposable subscription;
	private volatile boolean running;

	public AlertChangeListener(DatabaseClient client, R2dbcProperties properties) {
		this.client = client;
		this.connectionFactory = dedicatedFactory(properties);
	}

	/** A non-pooling factory: the LISTEN connection lives for the whole process, not one request. */
	private static ConnectionFactory dedicatedFactory(R2dbcProperties properties) {
		var password = properties.getPassword() == null ? "" : properties.getPassword();
		var options = ConnectionFactoryOptions.parse(properties.getUrl())
				.mutate()
				.option(ConnectionFactoryOptions.USER, properties.getUsername())
				.option(ConnectionFactoryOptions.PASSWORD, password)
				.build();
		return ConnectionFactories.get(options);
	}

	public Flux<MedicalAlert> changes() {
		return sink.asFlux();
	}

	@Override
	public void start() {
		if (running) {
			return;
		}
		running = true;
		subscription = listen()
				.doOnNext(alert -> sink.tryEmitNext(alert))
				.doOnError(error -> {
					lostAt.compareAndSet(null, Instant.now());
					log.warn("Alert LISTEN connection lost; will replay missed changes", error);
				})
				.retryWhen(Retry.backoff(Long.MAX_VALUE, FIRST_BACKOFF).maxBackoff(MAX_BACKOFF))
				.subscribe(null, error -> log.error("Alert change feed stopped", error));
	}

	@Override
	public void stop() {
		running = false;
		if (subscription != null) {
			subscription.dispose();
		}
		sink.tryEmitComplete();
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public boolean isAutoStartup() {
		return true;
	}

	private Flux<MedicalAlert> listen() {
		return Mono.from(connectionFactory.create())
				.flatMapMany(connection -> usingConnection((PostgresqlConnection) connection, lostAt.get()));
	}

	private Flux<MedicalAlert> usingConnection(PostgresqlConnection connection, Instant disconnectedAt) {
		return Flux.usingWhen(
				Mono.just(connection),
				current -> Mono.from(current.createStatement("LISTEN " + CHANNEL).execute())
						.thenMany(Flux.merge(notifications(current), catchUp(disconnectedAt)))
						// A connection that ends without an error must also trigger the retry/backoff path.
						.concatWith(Flux.error(new IllegalStateException("LISTEN connection closed"))),
				PostgresqlConnection::close,
				(current, error) -> current.close(),
				PostgresqlConnection::close);
	}

	private Flux<MedicalAlert> notifications(PostgresqlConnection connection) {
		return connection.getNotifications()
				.map(notification -> UUID.fromString(notification.getParameter()))
				.flatMap(this::findById);
	}

	private Flux<MedicalAlert> catchUp(Instant disconnectedAt) {
		if (disconnectedAt == null) {
			return Flux.empty();
		}
		return client.sql(PostgresMedicalAlertRepository.SELECT_CHANGED_SINCE)
				.bind("since", disconnectedAt.minus(CATCH_UP_MARGIN))
				.map(MedicalAlertRowMapper::map)
				.all()
				.doOnComplete(() -> lostAt.set(null));
	}

	private Flux<MedicalAlert> findById(UUID id) {
		return client.sql(PostgresMedicalAlertRepository.SELECT_BY_ID)
				.bind("id", id)
				.map(MedicalAlertRowMapper::map)
				.first()
				.flux();
	}
}
