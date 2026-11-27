package com.vidacruz.triageservice.infrastructure.config;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vidacruz.triageservice.infrastructure.persistence.IdempotencyKeyRepository;

/** Opt-in cleanup of expired idempotency keys (DEC-15, G-T05); the interval comes from configuration. */
@Component
@ConditionalOnProperty(prefix = "app.idempotency", name = "cleanup-enabled", havingValue = "true")
public class IdempotencyCleanupScheduler {

	private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);

	private final IdempotencyKeyRepository repository;

	public IdempotencyCleanupScheduler(IdempotencyKeyRepository repository) {
		this.repository = repository;
	}

	@Scheduled(fixedDelayString = "${app.idempotency.cleanup-interval-ms}")
	public void deleteExpired() {
		repository.deleteExpired(Instant.now())
				.subscribe(count -> log.debug("Removed {} expired idempotency key(s)", count),
						error -> log.warn("Idempotency key cleanup failed", error));
	}
}
