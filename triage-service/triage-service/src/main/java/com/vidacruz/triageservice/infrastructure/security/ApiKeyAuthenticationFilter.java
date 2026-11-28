package com.vidacruz.triageservice.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

/**
 * Validates the {@code X-API-Key} header on every business endpoint (ADR-07, NFR-10). Health probes and the
 * published OpenAPI/Swagger documentation are public. The key value is never logged; failures are logged with a
 * correlation id only.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ApiKeyAuthenticationFilter implements WebFilter {

	static final String API_KEY_HEADER = "X-API-Key";
	static final String CORRELATION_HEADER = "X-Correlation-Id";
	static final String UNAUTHORIZED_CODE = "UNAUTHORIZED";
	static final String UNAUTHORIZED_MESSAGE = "Invalid or missing X-API-Key header";

	private static final Set<String> PUBLIC_PATHS = Set.of("/actuator/health", "/health", "/triage/actuator/health",
			"/triage/health", "/v3/api-docs", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui", "/webjars");
	private static final List<String> PUBLIC_PREFIXES = List.of("/actuator/health/", "/health/",
			"/triage/actuator/health/", "/triage/health/", "/v3/api-docs/", "/swagger-ui/", "/webjars/");
	private static final Logger LOGGER = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

	private final String expectedKey;
	private final ObjectMapper objectMapper;

	public ApiKeyAuthenticationFilter(String expectedKey, ObjectMapper objectMapper) {
		if (expectedKey == null || expectedKey.isBlank()) {
			throw new IllegalArgumentException("API key must not be blank");
		}
		this.expectedKey = expectedKey;
		this.objectMapper = objectMapper;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String path = exchange.getRequest().getPath().value();
		if (isPublic(path)) {
			return chain.filter(exchange);
		}
		String providedKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
		if (matches(providedKey)) {
			return chain.filter(exchange);
		}
		String correlationId = correlationId(exchange);
		LOGGER.warn("Rejected {} without a valid {} header (correlationId={})", path, API_KEY_HEADER, correlationId);
		return unauthorized(exchange, correlationId);
	}

	private static boolean isPublic(String path) {
		return PUBLIC_PATHS.contains(path) || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
	}

	private boolean matches(String providedKey) {
		if (providedKey == null || providedKey.isBlank()) {
			return false;
		}
		return MessageDigest.isEqual(providedKey.getBytes(StandardCharsets.UTF_8),
				expectedKey.getBytes(StandardCharsets.UTF_8));
	}

	private static String correlationId(ServerWebExchange exchange) {
		String header = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
		return header != null && !header.isBlank() ? header : UUID.randomUUID().toString();
	}

	private Mono<Void> unauthorized(ServerWebExchange exchange, String correlationId) {
		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(HttpStatus.UNAUTHORIZED);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		response.getHeaders().set(CORRELATION_HEADER, correlationId);
		byte[] body;
		try {
			body = objectMapper.writeValueAsBytes(new UnauthorizedResponse(UNAUTHORIZED_CODE, UNAUTHORIZED_MESSAGE,
					correlationId));
		} catch (JsonProcessingException exception) {
			body = ("{\"code\":\"" + UNAUTHORIZED_CODE + "\",\"message\":\"" + UNAUTHORIZED_MESSAGE
					+ "\",\"correlationId\":\"" + correlationId + "\"}").getBytes(StandardCharsets.UTF_8);
		}
		return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
	}

	/** NFR-20 shape for authentication failures; reused by both services. */
	public record UnauthorizedResponse(String code, String message, String correlationId) {
	}
}
