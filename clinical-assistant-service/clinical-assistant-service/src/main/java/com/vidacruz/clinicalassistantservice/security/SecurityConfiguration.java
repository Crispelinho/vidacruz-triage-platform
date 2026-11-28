package com.vidacruz.clinicalassistantservice.security;

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
 * <p>Corpus ingestion needs {@link ClinicalScopes#CORPUS_ADMIN}, so a key issued to a query client cannot add
 * fragments to the citation allow-list (prepares the A-2 fix). Unknown routes are denied.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfiguration {

	static final String FORBIDDEN_CODE = "FORBIDDEN";
	static final String FORBIDDEN_MESSAGE = "The X-API-Key does not grant the scope this operation requires";

	/**
	 * Fail-closed (ADR-07): no default key. With {@code assistant.api-key} blank,
	 * {@link ApiKeyRegistry#create} throws and startup stops instead of accepting a public key.
	 */
	@Bean
	ApiKeyRegistry assistantApiKeyRegistry(@Value("${assistant.api-key:}") String ownerApiKey,
			@Value("${assistant.reviewer-api-key:}") String reviewerApiKey,
			@Value("${assistant.api-keys:}") String apiKeys) {
		return ApiKeyRegistry.create(ownerApiKey, reviewerApiKey, ClinicalScopes.REVIEWER, apiKeys,
				ClinicalScopes.ALL);
	}

	/** Runs right after CorrelationIdWebFilter, so a 401 carries the request's correlation id. */
	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 1)
	WebFilter apiKeyAuthenticationFilter(ApiKeyRegistry assistantApiKeyRegistry, ObjectMapper objectMapper) {
		return new ApiKeyAuthenticationFilter(assistantApiKeyRegistry, objectMapper);
	}

	@Bean
	SecurityWebFilterChain scopedFilterChain(ServerHttpSecurity http, ObjectMapper objectMapper) {
		String query = ClinicalScopes.authority(ClinicalScopes.QUERY);
		return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				// Stateless: the key is sent on every request; nothing is stored in a session.
				.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers("/actuator/health", "/actuator/health/**", "/health", "/health/**").permitAll()
						.pathMatchers("/actuator/**").hasAuthority(ClinicalScopes.authority(ClinicalScopes.METRICS))
						.pathMatchers(HttpMethod.POST, "/clinical/corpus/ingest")
						.hasAuthority(ClinicalScopes.authority(ClinicalScopes.CORPUS_ADMIN))
						.pathMatchers(HttpMethod.GET, "/clinical/corpus/retrieve").hasAuthority(query)
						.pathMatchers(HttpMethod.POST, "/asistente/consulta", "/rag").hasAuthority(query)
						.pathMatchers(HttpMethod.GET, "/asistente/estado-urgencias").hasAuthority(query)
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
