package com.vidacruz.triageservice.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vidacruz.triageservice.domain.service.SlaEvaluator;

/** Opt-in scheduler for RF-03; the interval is supplied by configuration, not by code. */
@Component
@ConditionalOnProperty(prefix = "triage.sla", name = "evaluation-enabled", havingValue = "true")
public class SlaEvaluationScheduler {

	private static final Logger log = LoggerFactory.getLogger(SlaEvaluationScheduler.class);

	private final SlaEvaluator evaluator;

	public SlaEvaluationScheduler(SlaEvaluator evaluator) {
		this.evaluator = evaluator;
	}

	@Scheduled(fixedDelayString = "${triage.sla.evaluation-interval}")
	public void evaluate() {
		evaluator.evaluateOnce()
				.subscribe(count -> log.debug("SLA evaluation escalated {} patient(s)", count),
						error -> log.warn("SLA evaluation failed", error));
	}
}
