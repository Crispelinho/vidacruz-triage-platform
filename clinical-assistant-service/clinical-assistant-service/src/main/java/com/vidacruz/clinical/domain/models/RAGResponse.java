package com.vidacruz.clinical.domain.models;

import java.util.List;
import java.util.Objects;

/**
 * Result of the RAG use case: an answer and the evidence it was grounded on.
 *
 * <p>When no evidence exists the answer is the fixed notice and {@code sources} is empty (RN-05, AMB-08).
 */
public record RAGResponse(String answer, List<EvidenceFragment> sources) {

	public static final String NO_PROTOCOL_NOTICE = "no existe protocolo disponible";

	public RAGResponse {
		if (answer == null || answer.isBlank()) {
			throw new IllegalArgumentException("answer must not be blank");
		}
		sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
	}

	public static RAGResponse noEvidence() {
		return new RAGResponse(NO_PROTOCOL_NOTICE, List.of());
	}

	public boolean hasEvidence() {
		return !sources.isEmpty();
	}

}
