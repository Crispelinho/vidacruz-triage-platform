package com.vidacruz.clinical.application.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

class CorrelationIdWebFilterTest {

	private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

	@Test
	void keepsTheCallerIdOnRequestResponseAndContext() {
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/asistente/consulta")
				.header(CorrelationId.HEADER, " corr-42 "));
		var seen = new Seen();

		filter.filter(exchange, seen).block();

		assertThat(seen.requestHeader).hasValue("corr-42");
		assertThat(seen.contextValue).hasValue("corr-42");
		assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationId.HEADER)).isEqualTo("corr-42");
	}

	@Test
	void generatesOneIdAndSharesItDownstreamWhenTheHeaderIsMissing() {
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/asistente/consulta"));
		var seen = new Seen();

		filter.filter(exchange, seen).block();

		String generated = exchange.getResponse().getHeaders().getFirst(CorrelationId.HEADER);
		assertThat(generated).isNotBlank();
		assertThat(seen.requestHeader).hasValue(generated);
		assertThat(seen.contextValue).hasValue(generated);
	}

	/** Captures what the next filter in the chain observes. */
	private static final class Seen implements WebFilterChain {

		final AtomicReference<String> requestHeader = new AtomicReference<>();
		final AtomicReference<String> contextValue = new AtomicReference<>();

		@Override
		public Mono<Void> filter(ServerWebExchange exchange) {
			requestHeader.set(exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER));
			return Mono.deferContextual(context -> {
				contextValue.set(context.get(CorrelationId.CONTEXT_KEY));
				return Mono.<Void>empty();
			});
		}
	}
}
