package com.vidacruz.triageservice.application.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import reactor.core.publisher.Mono;

/** A 5xx answer must mark the server span as failed, even when a controller advice handled the exception. */
class ServerErrorObservationWebFilterTest {

	/** What tracing handlers receive: only errors signalled through the observation mark a span. */
	private final List<Throwable> signalled = new ArrayList<>();

	@ParameterizedTest
	@ValueSource(ints = { 500, 502, 503 })
	void signalsAFixedErrorForAServerErrorResponse(int status) {
		handle(exchange -> {
			exchange.getResponse().setStatusCode(HttpStatus.valueOf(status));
			return exchange.getResponse().setComplete();
		});

		assertThat(signalled).singleElement().satisfies(error -> {
			assertThat(error).hasMessage("HTTP " + status).hasNoCause();
			assertThat(error.getStackTrace()).isEmpty();
		});
	}

	@ParameterizedTest
	@ValueSource(ints = { 200, 401, 404, 409 })
	void leavesOtherResponsesAlone(int status) {
		handle(exchange -> {
			exchange.getResponse().setStatusCode(HttpStatus.valueOf(status));
			return exchange.getResponse().setComplete();
		});

		assertThat(signalled).isEmpty();
	}

	/** Spring already marks the observation when an exception escapes the controllers; marking again adds nothing. */
	@Test
	void doesNotSignalASecondTimeForAFailureTheFrameworkAlreadyReports() {
		IllegalStateException failure = new IllegalStateException("storage unavailable");

		handle(exchange -> Mono.error(failure));

		assertThat(signalled).containsExactly(failure);
	}

	private void handle(WebHandler handler) {
		ObservationRegistry registry = ObservationRegistry.create();
		registry.observationConfig().observationHandler(new ObservationHandler<>() {
			@Override
			public void onError(Observation.Context context) {
				signalled.add(context.getError());
			}

			@Override
			public boolean supportsContext(Observation.Context context) {
				return true;
			}
		});
		HttpHandler http = WebHttpHandlerBuilder.webHandler(handler)
				.filter(new ServerErrorObservationWebFilter())
				// As in the running application, an exception handler turns failures into responses.
				.exceptionHandler((exchange, failure) -> {
					exchange.getResponse().setStatusCode(failure instanceof ResponseStatusException status
							? status.getStatusCode()
							: HttpStatus.INTERNAL_SERVER_ERROR);
					return exchange.getResponse().setComplete();
				})
				.observationRegistry(registry)
				.build();
		http.handle(MockServerHttpRequest.get("/probe").build(), new MockServerHttpResponse()).block();
	}
}
