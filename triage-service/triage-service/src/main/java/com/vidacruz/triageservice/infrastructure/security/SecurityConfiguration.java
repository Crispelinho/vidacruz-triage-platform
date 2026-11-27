package com.vidacruz.triageservice.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.WebFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Wires the API-key filter (ADR-07) and removes Spring Security's generated basic-auth chain. */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfiguration {

	/** Runs right after CorrelationIdWebFilter, so a 401 carries the request's correlation id. */
	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 1)
	WebFilter apiKeyAuthenticationFilter(@Value("${triage.api-key}") String apiKey, ObjectMapper objectMapper) {
		return new ApiKeyAuthenticationFilter(apiKey, objectMapper);
	}

	/**
	 * Authorization is decided by {@link ApiKeyAuthenticationFilter}; the security chain must not
	 * reject a request that already carried a valid key.
	 */
	@Bean
	SecurityWebFilterChain permitAllFilterChain(ServerHttpSecurity http) {
		return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
				.build();
	}
}
