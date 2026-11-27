package com.vidacruz.clinicalassistantservice.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
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

	private static final Set<String> PUBLIC_PATHS = Set.of("/actuator/health", "/health", "/asistente/actuator/health",
			"/asistente/health", "/v3/api-docs", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui", "/webjars");
	private static final List<String> PUBLIC_PREFIXES = List.of("/actuator/health/", "/health/",
			"/asistente/actuator/health/", "/asistente/health/", "/v3/api-docs/", "/swagger-ui/", "/webjars/");
	private static final Logger LOGGER = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

	/** Exchange attribute holding the authenticated key id, for audit logging. Never the key value. */
	public static final String KEY_ID_ATTRIBUTE = ApiKeyAuthenticationFilter.class.getName() + ".keyId";

	private final ApiKeyRegistry registry;
	private final ObjectMapper objectMapper;

	/** Single-key mode: the key gets every scope (backward compatible with the pre-M-2 configuration). */
	public ApiKeyAuthenticationFilter(String expectedKey, ObjectMapper objectMapper) {
		this(ApiKeyRegistry.single(expectedKey, ClinicalScopes.ALL), objectMapper);
	}

	public ApiKeyAuthenticationFilter(ApiKeyRegistry registry, ObjectMapper objectMapper) {
		this.registry = Objects.requireNonNull(registry, "registry");
		this.objectMapper = objectMapper;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String path = exchange.getRequest().getPath().value();
		if (isPublic(path)) {
			return chain.filter(exchange);
		}
		String providedKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
		Optional<ApiKeyRegistry.Principal> principal = registry.authenticate(providedKey);
		if (principal.isPresent()) {
			exchange.getAttributes().put(KEY_ID_ATTRIBUTE, principal.get().keyId());
			// Route authorization (scopes) is decided by the SecurityWebFilterChain from this authentication.
			return chain.filter(exchange)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(principal.get())));
		}
		String correlationId = correlationId(exchange);
		LOGGER.warn("Rejected {} without a valid {} header (correlationId={})", path, API_KEY_HEADER, correlationId);
		return unauthorized(exchange, correlationId);
	}

	private static boolean isPublic(String path) {
		return PUBLIC_PATHS.contains(path) || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
	}

	private static Authentication authentication(ApiKeyRegistry.Principal principal) {
		List<SimpleGrantedAuthority> authorities = principal.scopes().stream()
				.map(ClinicalScopes::authority)
				.map(SimpleGrantedAuthority::new)
				.toList();
		return UsernamePasswordAuthenticationToken.authenticated(principal.keyId(), null, authorities);
	}

	static String correlationId(ServerWebExchange exchange) {
		String header = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
		return header != null && !header.isBlank() ? header : UUID.randomUUID().toString();
	}

	private Mono<Void> unauthorized(ServerWebExchange exchange, String correlationId) {
		return writeError(exchange, objectMapper, HttpStatus.UNAUTHORIZED, UNAUTHORIZED_CODE, UNAUTHORIZED_MESSAGE,
				correlationId);
	}

	/** Writes the NFR-20 error body; also used for 403 when a valid key lacks the route's scope. */
	static Mono<Void> writeError(ServerWebExchange exchange, ObjectMapper objectMapper, HttpStatus status, String code,
			String message, String correlationId) {
		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(status);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		response.getHeaders().set(CORRELATION_HEADER, correlationId);
		byte[] body;
		try {
			body = objectMapper.writeValueAsBytes(new UnauthorizedResponse(code, message, correlationId));
		} catch (JsonProcessingException exception) {
			body = ("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"correlationId\":\"" + correlationId
					+ "\"}").getBytes(StandardCharsets.UTF_8);
		}
		return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
	}

	/** NFR-20 shape for authentication failures; reused by both services. */
	public record UnauthorizedResponse(String code, String message, String correlationId) {
	}
}
