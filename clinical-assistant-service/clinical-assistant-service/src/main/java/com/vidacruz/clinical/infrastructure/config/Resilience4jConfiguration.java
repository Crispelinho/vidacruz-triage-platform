package com.vidacruz.clinical.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Circuit breaker foundation for the LLM (ADR-09). Passive on purpose: the breaker is configured and exposed as a
 * bean, but nothing wraps {@code LLMProvider} with it yet. Wiring it, the 503 fallback and metrics belong to the
 * {@code assistant-llm-resilience} change.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CircuitBreakerProperties.class)
class Resilience4jConfiguration {

	static final String LLM_CIRCUIT_BREAKER = "llm";

	// Named distinctly so it cannot clash with beans from the Spring Cloud / Resilience4j auto-configuration.
	@Bean
	CircuitBreaker llmCircuitBreaker(CircuitBreakerProperties properties) {
		CircuitBreakerConfig config = CircuitBreakerConfig.custom()
				.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
				.slidingWindowSize(properties.slidingWindowSize())
				.minimumNumberOfCalls(properties.minimumNumberOfCalls())
				.failureRateThreshold(properties.failureRateThreshold())
				.slowCallRateThreshold(properties.slowCallRateThreshold())
				.slowCallDurationThreshold(properties.slowCallDurationThreshold())
				.waitDurationInOpenState(properties.waitDurationInOpenState())
				.permittedNumberOfCallsInHalfOpenState(properties.permittedCallsInHalfOpenState())
				.automaticTransitionFromOpenToHalfOpenEnabled(true)
				.build();
		return CircuitBreakerRegistry.of(config).circuitBreaker(LLM_CIRCUIT_BREAKER);
	}

}
