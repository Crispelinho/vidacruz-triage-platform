package com.vidacruz.clinical.application.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.micrometer.observation.Observation;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import reactor.core.publisher.Mono;

/**
 * Marks the server observation, and so its span, as failed when a handled request answers 5xx (NFR-16). A controller
 * advice that turns an exception into a response leaves no error behind, so without this a 503 reads as a success in
 * the trace. Only a fixed message with the status code is recorded: exception texts can carry request data. Failures
 * that escape the controllers are already reported by the framework and are left to it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ServerErrorObservationWebFilter implements WebFilter {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		// Read here, outside the security filters: the current observation is still the server request itself.
		return Mono.deferContextual(context -> {
			Observation request = context.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
			if (request == null) {
				return chain.filter(exchange);
			}
			return chain.filter(exchange)
					.doOnSuccess(done -> mark(request, exchange.getResponse().getStatusCode()));
		});
	}

	private static void mark(Observation request, HttpStatusCode status) {
		if (status != null && status.is5xxServerError()) {
			request.error(new ServerErrorResponse(status.value()));
		}
	}

	/** Carries the status only: no cause and no stack trace, so nothing else can reach the span. */
	static final class ServerErrorResponse extends RuntimeException {

		private ServerErrorResponse(int status) {
			super("HTTP " + status, null, false, false);
		}
	}
}
