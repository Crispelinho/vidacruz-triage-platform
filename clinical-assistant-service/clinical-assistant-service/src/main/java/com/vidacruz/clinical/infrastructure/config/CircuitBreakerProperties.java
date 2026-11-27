package com.vidacruz.clinical.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Per-node circuit breaker thresholds (ADR-09, NFR-08, C17 developer record). The values are recorded by the
 * Developer/User; each chain node gets its own instance from the registry. There is no retry against the same
 * provider; failover moves to the next node.
 */
@ConfigurationProperties(prefix = "clinical.resilience.llm-circuit-breaker")
public record CircuitBreakerProperties(@DefaultValue("10") int slidingWindowSize,
		@DefaultValue("10") int minimumNumberOfCalls, @DefaultValue("50") float failureRateThreshold,
		@DefaultValue("100") float slowCallRateThreshold, @DefaultValue("20s") Duration slowCallDurationThreshold,
		@DefaultValue("30s") Duration waitDurationInOpenState, @DefaultValue("3") int permittedCallsInHalfOpenState) {
}
