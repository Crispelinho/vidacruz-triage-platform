package com.vidacruz.triage.infrastructure.scheduling;

import com.vidacruz.triage.domain.service.SlaEscalationService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * RF-03: periodically triggers the SLA evaluator. The tick only subscribes to the reactive pipeline, so the
 * scheduler thread never blocks on R2DBC. Spring's default scheduler is single-threaded, and the guard below
 * keeps a slow run from overlapping the next tick within this instance. Across replicas, the conditional
 * update in {@link SlaEscalationService} decides the winner (RN-03).
 *
 * <p>Interval: {@code triage.sla.evaluation-interval}, in milliseconds or a Duration string
 * (e.g. {@code 30s}); defaults to 60000. The first run waits one interval so startup is not noisy.
 */
@Component
@EnableScheduling
public class SlaEvaluationScheduler {

	private static final Logger log = LoggerFactory.getLogger(SlaEvaluationScheduler.class);

	private final SlaEscalationService service;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public SlaEvaluationScheduler(SlaEscalationService service) {
		this.service = service;
	}

	@Scheduled(fixedDelayString = "${triage.sla.evaluation-interval:60000}",
			initialDelayString = "${triage.sla.evaluation-interval:60000}")
	void evaluate() {
		if (!running.compareAndSet(false, true)) {
			log.debug("Previous SLA evaluation still running; skipping this tick");
			return;
		}
		service.evaluate()
				.doOnNext(count -> {
					if (count > 0) {
						log.info("SLA evaluation escalated {} patient(s)", count);
					}
				})
				.doOnError(e -> log.error("SLA evaluation run failed; retrying on the next interval", e))
				.onErrorResume(e -> Mono.empty())
				.doFinally(signal -> running.set(false))
				.subscribe();
	}
}
