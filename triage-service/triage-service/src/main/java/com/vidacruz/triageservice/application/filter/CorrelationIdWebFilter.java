package com.vidacruz.triageservice.application.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/** Propagates X-Correlation-Id into the Reactor context and echoes it on the response. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String correlationId = CorrelationId.fromHeader(exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER));
		exchange.getResponse().getHeaders().set(CorrelationId.HEADER, correlationId);
		return chain.filter(exchange)
				.contextWrite(context -> context.put(CorrelationId.CONTEXT_KEY, correlationId));
	}
}
