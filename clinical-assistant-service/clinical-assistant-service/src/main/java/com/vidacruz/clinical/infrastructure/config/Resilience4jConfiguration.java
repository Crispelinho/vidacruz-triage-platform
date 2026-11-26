package com.vidacruz.clinical.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Circuit breaker foundation for the LLM (ADR-09, C17). One registry supplies an independent breaker per chain node;
 * the router decides which node to call. The legacy {@code llm} breaker remains exposed for compatibility with the
 * scaffold tests and is not the breaker used by the chain.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CircuitBreakerProperties.class)
class Resilience4jConfiguration {

	static final String LLM_CIRCUIT_BREAKER = "llm";

	// Named and primary so it cannot clash with beans from the Spring Cloud / Resilience4j auto-configuration.
	@Bean
	@Primary
	CircuitBreakerRegistry llmCircuitBreakerRegistry(CircuitBreakerProperties properties) {
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
		return CircuitBreakerRegistry.of(config);
	}

	@Bean
	CircuitBreaker llmCircuitBreaker(CircuitBreakerRegistry llmCircuitBreakerRegistry) {
		return llmCircuitBreakerRegistry.circuitBreaker(LLM_CIRCUIT_BREAKER);
	}

}
