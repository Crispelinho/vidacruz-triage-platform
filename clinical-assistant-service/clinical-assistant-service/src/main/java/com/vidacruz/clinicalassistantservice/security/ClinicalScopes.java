package com.vidacruz.clinicalassistantservice.security;

import java.util.Set;

/** API-key scopes of clinical-assistant-service (M-2). Spring Security sees each one as {@code SCOPE_<scope>}. */
public final class ClinicalScopes {

	/** Clinical questions, retrieval and the urgency projection: POST /asistente/consulta, /rag and their reads. */
	public static final String QUERY = "assistant:query";

	/** Corpus ingestion: POST /clinical/corpus/ingest. Kept apart from QUERY so a query key cannot poison the corpus. */
	public static final String CORPUS_ADMIN = "corpus:admin";

	/** /actuator/** other than health probes: the Prometheus scrape. */
	public static final String METRICS = "metrics:read";

	public static final Set<String> ALL = Set.of(QUERY, CORPUS_ADMIN, METRICS);

	/** The optional, revocable evaluator key: questions and reads, never corpus ingestion. */
	public static final Set<String> REVIEWER = Set.of(QUERY);

	private ClinicalScopes() {
	}

	static String authority(String scope) {
		return "SCOPE_" + scope;
	}

}
