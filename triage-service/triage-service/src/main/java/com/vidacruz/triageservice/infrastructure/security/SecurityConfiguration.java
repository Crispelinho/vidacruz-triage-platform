package com.vidacruz.triageservice.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.server.WebFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wires the API-key filter (ADR-07) and authorizes each route by key scope (M-2).
 *
 * <p>{@link ApiKeyAuthenticationFilter} authenticates the key and puts its scopes in the reactive security context;
 * this chain decides whether those scopes cover the route. Unknown routes are denied.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfiguration {

	static final String FORBIDDEN_CODE = "FORBIDDEN";
	static final String FORBIDDEN_MESSAGE = "The X-API-Key does not grant the scope this operation requires";

	/**
	 * Fail-closed (ADR-07): no default key. With {@code triage.api-key} blank,
	 * {@link ApiKeyRegistry#create} throws and startup stops instead of accepting a public key.
	 */
	@Bean
	ApiKeyRegistry triageApiKeyRegistry(@Value("${triage.api-key:}") String ownerApiKey,
			@Value("${triage.reviewer-api-key:}") String reviewerApiKey, @Value("${triage.api-keys:}") String apiKeys) {
		return ApiKeyRegistry.create(ownerApiKey, reviewerApiKey, TriageScopes.REVIEWER, apiKeys, TriageScopes.ALL);
	}

	/** Runs right after CorrelationIdWebFilter, so a 401 carries the request's correlation id. */
	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 1)
	WebFilter apiKeyAuthenticationFilter(ApiKeyRegistry triageApiKeyRegistry, ObjectMapper objectMapper) {
		return new ApiKeyAuthenticationFilter(triageApiKeyRegistry, objectMapper);
	}

	/** After authentication, so the audit line can name the key id that read the record. */
	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 2)
	WebFilter phiReadAuditFilter() {
		return new PhiReadAuditFilter();
	}

	@Bean
	SecurityWebFilterChain scopedFilterChain(ServerHttpSecurity http, ObjectMapper objectMapper) {
		return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				// Stateless: the key is sent on every request; nothing is stored in a session.
				.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers("/actuator/health", "/actuator/health/**", "/health", "/health/**").permitAll()
						.pathMatchers("/actuator/**").hasAuthority(TriageScopes.authority(TriageScopes.METRICS))
						.pathMatchers(HttpMethod.GET, "/triage/**").hasAuthority(TriageScopes.authority(TriageScopes.READ))
						.pathMatchers(HttpMethod.POST, "/triage/**")
						.hasAuthority(TriageScopes.authority(TriageScopes.WRITE))
						.pathMatchers(HttpMethod.PATCH, "/triage/**")
						.hasAuthority(TriageScopes.authority(TriageScopes.WRITE))
						.anyExchange().denyAll())
				.exceptionHandling(handling -> handling
						.accessDeniedHandler((exchange, denied) -> ApiKeyAuthenticationFilter.writeError(exchange,
								objectMapper, HttpStatus.FORBIDDEN, FORBIDDEN_CODE, FORBIDDEN_MESSAGE,
								ApiKeyAuthenticationFilter.correlationId(exchange)))
						.authenticationEntryPoint((exchange, missing) -> ApiKeyAuthenticationFilter.writeError(exchange,
								objectMapper, HttpStatus.UNAUTHORIZED, ApiKeyAuthenticationFilter.UNAUTHORIZED_CODE,
								ApiKeyAuthenticationFilter.UNAUTHORIZED_MESSAGE,
								ApiKeyAuthenticationFilter.correlationId(exchange))))
				.build();
	}

}
