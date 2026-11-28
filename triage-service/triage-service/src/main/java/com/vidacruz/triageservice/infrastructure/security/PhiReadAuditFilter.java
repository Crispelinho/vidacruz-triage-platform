package com.vidacruz.triageservice.infrastructure.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * Audit trail for PHI reads (M-2): every {@code GET /triage/pacientes/{id}} with an authenticated key writes one line
 * on the {@value #AUDIT_LOGGER} logger with the key id, the patient id, the outcome and the correlation id.
 *
 * <p>The line never contains the key value or any patient field: the patient id is the opaque UUID from the path.
 * Runs after {@link ApiKeyAuthenticationFilter}, so requests rejected for a missing or wrong key are not PHI reads
 * and are already logged there.
 */
public final class PhiReadAuditFilter implements WebFilter {

	static final String AUDIT_LOGGER = "audit.phi";

	private static final Logger AUDIT = LoggerFactory.getLogger(AUDIT_LOGGER);

	private static final Pattern PATIENT_DETAIL = Pattern.compile("^/triage/pacientes/([^/]+)/?$");

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		if (!HttpMethod.GET.equals(exchange.getRequest().getMethod())) {
			return chain.filter(exchange);
		}
		Matcher matcher = PATIENT_DETAIL.matcher(exchange.getRequest().getPath().pathWithinApplication().value());
		Object keyId = exchange.getAttribute(ApiKeyAuthenticationFilter.KEY_ID_ATTRIBUTE);
		if (!matcher.matches() || keyId == null) {
			return chain.filter(exchange);
		}
		String patientId = sanitize(matcher.group(1));
		String correlationId = ApiKeyAuthenticationFilter.correlationId(exchange);
		return chain.filter(exchange).doFinally(signal -> {
			HttpStatusCode status = exchange.getResponse().getStatusCode();
			// A handler that completes without setting a status answers with WebFlux's default 200.
			Object outcome = status != null ? Integer.valueOf(status.value())
					: signal == SignalType.ON_COMPLETE ? Integer.valueOf(200) : "unknown";
			AUDIT.info("PHI read route=GET /triage/pacientes/{id} patientId={} keyId={} status={} signal={} "
					+ "correlationId={}", patientId, keyId, outcome, signal, sanitize(correlationId));
		});
	}

	/** Path and header values are caller-controlled: keep them short and printable in the audit line. */
	private static String sanitize(String value) {
		String printable = value.replaceAll("[^A-Za-z0-9._-]", "_");
		return printable.length() > 64 ? printable.substring(0, 64) : printable;
	}

}
