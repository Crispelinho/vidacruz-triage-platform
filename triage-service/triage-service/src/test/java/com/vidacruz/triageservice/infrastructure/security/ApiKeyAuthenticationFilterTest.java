package com.vidacruz.triageservice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

class ApiKeyAuthenticationFilterTest {

	private static final String API_KEY = "test-triage-key";
	private static final String BUSINESS_PATH = "/triage/pacientes/00000000-0000-0000-0000-000000000000";

	private final ObjectMapper objectMapper = new ObjectMapper();
	private ApiKeyAuthenticationFilter filter;
	private AtomicBoolean chainCalled;
	private WebFilterChain chain;

	@BeforeEach
	void setUp() {
		filter = new ApiKeyAuthenticationFilter(API_KEY, objectMapper);
		chainCalled = new AtomicBoolean(false);
		chain = exchange -> {
			chainCalled.set(true);
			return Mono.empty();
		};
	}

	@Test
	void allowsHealthProbeWithoutKey() {
		MockServerWebExchange exchange = MockServerWebExchange
				.from(MockServerHttpRequest.get("/actuator/health/readiness"));

		filter.filter(exchange, chain).block();

		assertThat(chainCalled).isTrue();
		assertThat(exchange.getResponse().getStatusCode()).isNull();
	}

	@Test
	void allowsPrefixedHealthProbeAndSwaggerDocsWithoutKey() {
		for (String path : List.of("/triage/actuator/health/readiness", "/v3/api-docs", "/v3/api-docs/swagger-config",
				"/swagger-ui.html", "/swagger-ui/index.html", "/webjars/swagger-ui/index.css")) {
			filter.filter(MockServerWebExchange.from(MockServerHttpRequest.get(path)), chain).block();
			assertThat(chainCalled).as(path).isTrue();
			chainCalled.set(false);
		}
	}

	@Test
	void allowsRequestWithValidKey() {
		MockServerWebExchange exchange = MockServerWebExchange
				.from(MockServerHttpRequest.get(BUSINESS_PATH).header("X-API-Key", API_KEY));

		filter.filter(exchange, chain).block();

		assertThat(chainCalled).isTrue();
	}

	@Test
	void rejectsMissingKeyWithStructuredUnauthorized() throws Exception {
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(BUSINESS_PATH));

		filter.filter(exchange, chain).block();

		assertThat(chainCalled).isFalse();
		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
		assertThat(body.get("code").asText()).isEqualTo("UNAUTHORIZED");
		assertThat(body.get("message").asText()).isEqualTo("Invalid or missing X-API-Key header");
		assertThat(body.get("correlationId").asText()).isNotBlank();
		assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-Id"))
				.isEqualTo(body.get("correlationId").asText());
	}

	@Test
	void rejectsWrongKey() {
		MockServerWebExchange exchange = MockServerWebExchange
				.from(MockServerHttpRequest.get(BUSINESS_PATH).header("X-API-Key", "wrong-key"));

		filter.filter(exchange, chain).block();

		assertThat(chainCalled).isFalse();
		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void preservesIncomingCorrelationId() throws Exception {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get(BUSINESS_PATH).header("X-Correlation-Id", "demo-123"));

		filter.filter(exchange, chain).block();

		JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
		assertThat(body.get("correlationId").asText()).isEqualTo("demo-123");
	}
}
