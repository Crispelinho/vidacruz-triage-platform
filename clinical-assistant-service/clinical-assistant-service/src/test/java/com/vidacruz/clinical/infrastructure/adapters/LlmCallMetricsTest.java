package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LlmCallMetricsTest {

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

	private final LlmCallMetrics metrics = new LlmCallMetrics(registry);

	@Test
	void timesEachAttemptUnderHowItEnded() {
		StepVerifier.create(metrics.timed(Mono.delay(Duration.ofMillis(50))))
				.expectNextCount(1).verifyComplete();
		StepVerifier.create(metrics.timed(Mono.error(new TimeoutException("too slow")))).verifyError();
		StepVerifier.create(metrics.timed(Mono.error(status(503)))).verifyError();

		assertThat(timer(LlmCallMetrics.SUCCESS).count()).isEqualTo(1);
		assertThat(timer(LlmCallMetrics.SUCCESS).totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(50.0d);
		assertThat(timer(LlmCallMetrics.TIMEOUT).count()).isEqualTo(1);
		assertThat(timer(LlmCallMetrics.SERVER_ERROR).count()).isEqualTo(1);
		assertThat(timer(LlmCallMetrics.REJECTED).count()).isZero();
	}

	@Test
	void startsTimingOnlyWhenTheAttemptIsSubscribed() {
		Mono<String> attempt = metrics.timed(Mono.just("answer"));

		assertThat(timer(LlmCallMetrics.SUCCESS).count()).isZero();
		StepVerifier.create(attempt).expectNext("answer").verifyComplete();
		StepVerifier.create(attempt).expectNext("answer").verifyComplete();
		assertThat(timer(LlmCallMetrics.SUCCESS).count()).isEqualTo(2);
	}

	@Test
	void exposesBucketsUpToTheNodeTimeout() {
		assertThat(timer(LlmCallMetrics.SUCCESS).takeSnapshot().histogramCounts())
				.extracting(bucket -> bucket.bucket(TimeUnit.SECONDS))
				.containsExactly(0.5d, 1.0d, 2.0d, 5.0d, 10.0d, 15.0d, 30.0d);
	}

	@ParameterizedTest
	@CsvSource({ "429, rate_limited", "408, timeout", "500, server_error", "529, server_error", "401, rejected",
			"400, rejected" })
	void namesTheOutcomeOfAnHttpAnswer(int code, String outcome) {
		assertThat(LlmCallMetrics.outcomeOf(status(code))).isEqualTo(outcome);
	}

	@Test
	void unwrapsTheFailoverExceptionToNameTheRealCause() {
		assertThat(LlmCallMetrics.outcomeOf(new LLMUnavailableException("node down", new TimeoutException())))
				.isEqualTo(LlmCallMetrics.TIMEOUT);
		assertThat(LlmCallMetrics.outcomeOf(new LLMUnavailableException("node down", new IOException("refused"))))
				.isEqualTo(LlmCallMetrics.NETWORK);
		assertThat(LlmCallMetrics.outcomeOf(new IllegalStateException("bug"))).isEqualTo(LlmCallMetrics.OTHER);
	}

	private Timer timer(String outcome) {
		return registry.get(LlmCallMetrics.LATENCY).tag(LlmCallMetrics.OUTCOME_TAG, outcome).timer();
	}

	private static WebClientResponseException status(int code) {
		return WebClientResponseException.create(code, "status", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
	}
}
