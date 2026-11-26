package com.vidacruz.clinical.domain.models;

import java.util.Objects;

/**
 * An evidence fragment with its relevance score from similarity search.
 *
 * @param fragment retrieved evidence
 * @param score    relevance in [0, 1], where 1 is most relevant; semantics depend on embedding model
 */
public record ScoredEvidenceFragment(EvidenceFragment fragment, double score) {

	public ScoredEvidenceFragment {
		Objects.requireNonNull(fragment, "fragment");
		if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
			throw new IllegalArgumentException("score must be between 0 and 1");
		}
	}

}
