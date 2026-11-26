package com.vidacruz.clinical.infrastructure.adapters;

import java.util.List;

import com.vidacruz.clinical.domain.models.EvidenceFragment;

/** Builds the same grounded prompt for every provider so failover does not change the question. */
final class GroundedPrompt {

	private GroundedPrompt() {
	}

	static String build(String question, List<EvidenceFragment> evidence) {
		StringBuilder prompt = new StringBuilder("Answer only from the numbered evidence. Preserve the [n] markers.\n\nQuestion: ")
				.append(question)
				.append("\n\nEvidence:\n");
		for (int i = 0; i < evidence.size(); i++) {
			prompt.append('[').append(i + 1).append("] ").append(evidence.get(i).content()).append('\n');
		}
		return prompt.toString();
	}

}
