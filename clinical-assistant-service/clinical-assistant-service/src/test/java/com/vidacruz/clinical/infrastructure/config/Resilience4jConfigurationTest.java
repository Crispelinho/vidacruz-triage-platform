package com.vidacruz.clinical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

class Resilience4jConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(Resilience4jConfiguration.class);

	@Test
	void exposesAClosedBreakerWithTheRecordedC17Defaults() {
		runner.run(context -> {
			CircuitBreaker breaker = context.getBean("llmCircuitBreaker", CircuitBreaker.class);
			CircuitBreakerConfig config = breaker.getCircuitBreakerConfig();

			assertThat(breaker.getName()).isEqualTo("llm");
			assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
			assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isZero();
			assertThat(config.getSlidingWindowSize()).isEqualTo(10);
			assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
			assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
			assertThat(config.getSlowCallRateThreshold()).isEqualTo(100f);
			assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(20));
			assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(30_000L);
			assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
			assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();
		});
	}

	@Test
	void thresholdsAreConfigurable() {
		runner.withPropertyValues("clinical.resilience.llm-circuit-breaker.failure-rate-threshold=30",
				"clinical.resilience.llm-circuit-breaker.wait-duration-in-open-state=5s",
				"clinical.resilience.llm-circuit-breaker.sliding-window-size=8",
				"clinical.resilience.llm-circuit-breaker.minimum-number-of-calls=4").run(context -> {
					CircuitBreakerConfig config = context.getBean(CircuitBreaker.class).getCircuitBreakerConfig();

					assertThat(config.getFailureRateThreshold()).isEqualTo(30f);
					assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(5_000L);
					assertThat(config.getSlidingWindowSize()).isEqualTo(8);
					assertThat(config.getMinimumNumberOfCalls()).isEqualTo(4);
				});
	}

	@Test
	void breakerOpensOnceTheFailureRateIsReachedAfterTheMinimumCalls() {
		runner.run(context -> {
			CircuitBreaker breaker = context.getBean(CircuitBreaker.class);

			for (int call = 0; call < 9; call++) {
				breaker.onError(1, TimeUnit.MILLISECONDS, new RuntimeException("llm down"));
			}
			assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

			breaker.onError(1, TimeUnit.MILLISECONDS, new RuntimeException("llm down"));
			assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
		});
	}

}
