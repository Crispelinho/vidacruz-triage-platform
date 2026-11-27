package com.vidacruz.triageservice.application.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Resolves X-Correlation-Id once per request, before authentication. The value is written back on the request so
 * the API-key filter reads the same id, echoed on the response and stored in the Reactor context for the outbox
 * envelope and the logging MDC.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String correlationId = CorrelationId.fromHeader(exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER));
		ServerWebExchange tagged = exchange.mutate()
				.request(request -> request.headers(headers -> headers.set(CorrelationId.HEADER, correlationId)))
				.build();
		tagged.getResponse().getHeaders().set(CorrelationId.HEADER, correlationId);
		return chain.filter(tagged)
				.contextWrite(context -> context.put(CorrelationId.CONTEXT_KEY, correlationId));
	}
}
