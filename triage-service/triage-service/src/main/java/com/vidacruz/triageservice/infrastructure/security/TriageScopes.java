package com.vidacruz.triageservice.infrastructure.security;

import java.util.Set;

/** API-key scopes of triage-service (M-2). Spring Security sees each one as the authority {@code SCOPE_<scope>}. */
public final class TriageScopes {

	/** GET on /triage/**: patient detail (PHI), alerts, the SSE stream and reports. */
	public static final String READ = "triage:read";

	/** POST/PATCH on /triage/**: registration, state changes and alert acknowledgement. */
	public static final String WRITE = "triage:write";

	/** /actuator/** other than health probes: the Prometheus scrape. */
	public static final String METRICS = "metrics:read";

	public static final Set<String> ALL = Set.of(READ, WRITE, METRICS);

	/** The optional, revocable evaluator key: the whole triage API, but not the metrics scrape. */
	public static final Set<String> REVIEWER = Set.of(READ, WRITE);

	private TriageScopes() {
	}

	static String authority(String scope) {
		return "SCOPE_" + scope;
	}

}
