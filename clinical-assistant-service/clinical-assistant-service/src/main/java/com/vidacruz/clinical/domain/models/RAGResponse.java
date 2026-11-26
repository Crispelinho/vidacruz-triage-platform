package com.vidacruz.clinical.domain.models;

import java.util.List;
import java.util.Objects;

/**
 * Result of the RAG use case: an answer and the evidence it was grounded on.
 *
 * <p>When no evidence exists the answer is the fixed notice, {@code sources} is empty and {@code modelo_usado} is
 * {@code none} because no LLM node was called (RN-05, AMB-08). Otherwise {@code modelo_usado} is the exact
 * {@code node/model} that answered, as required by the C17 failover record.
 */
public record RAGResponse(String answer, List<EvidenceFragment> sources, String modelo_usado) {

	public static final String NO_PROTOCOL_NOTICE = "no existe protocolo disponible";

	public static final String NO_MODEL = "none";

	public RAGResponse {
		if (answer == null || answer.isBlank()) {
			throw new IllegalArgumentException("answer must not be blank");
		}
		if (modelo_usado == null || modelo_usado.isBlank()) {
			throw new IllegalArgumentException("modelo_usado must not be blank");
		}
		sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
	}

	/** Compatibility constructor for callers that do not exercise the LLM chain. */
	public RAGResponse(String answer, List<EvidenceFragment> sources) {
		this(answer, sources, NO_MODEL);
	}

	public static RAGResponse noEvidence() {
		return new RAGResponse(NO_PROTOCOL_NOTICE, List.of(), NO_MODEL);
	}

	/**
	 * Provenance of every source, numbered like the markers in the answer. Derived from {@code sources}, so it can
	 * never reference a fragment that was not retrieved.
	 */
	public List<Citation> citations() {
		return Citation.fromEvidence(sources);
	}

	public boolean hasEvidence() {
		return !sources.isEmpty();
	}

}
