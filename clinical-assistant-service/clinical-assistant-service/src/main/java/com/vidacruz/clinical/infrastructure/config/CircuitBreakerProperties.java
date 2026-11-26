package com.vidacruz.clinical.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * LLM circuit breaker thresholds (ADR-09, NFR-08). The defaults are provisional: the numeric values and the failure
 * classification for 429 and 5xx are still open under G-05 and CA-G-05. There are no retries by design.
 */
@ConfigurationProperties(prefix = "clinical.resilience.llm-circuit-breaker")
public record CircuitBreakerProperties(@DefaultValue("20") int slidingWindowSize,
		@DefaultValue("10") int minimumNumberOfCalls, @DefaultValue("50") float failureRateThreshold,
		@DefaultValue("100") float slowCallRateThreshold, @DefaultValue("20s") Duration slowCallDurationThreshold,
		@DefaultValue("30s") Duration waitDurationInOpenState, @DefaultValue("3") int permittedCallsInHalfOpenState) {
}
