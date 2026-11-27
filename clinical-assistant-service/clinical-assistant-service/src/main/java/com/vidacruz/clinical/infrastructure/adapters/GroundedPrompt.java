package com.vidacruz.clinical.infrastructure.adapters;

import java.util.List;

import com.vidacruz.clinical.domain.models.EvidenceFragment;

/** Builds the same grounded prompt for every provider so failover does not change the question or the JSON contract. */
final class GroundedPrompt {

	private static final String INSTRUCTION = """
			Answer only from the numbered evidence. Return ONLY JSON with this shape:
			{"respuesta":"<answer text>","fuentes":[{"protocolo":"<code>","seccion":"<section>"}]}
			Every fuentes entry must be one of the protocol/section pairs listed below. Do not invent sources.
			You may use [n] markers in respuesta, but only for fragments you list in fuentes.
			""";

	private GroundedPrompt() {
	}

	static String build(String question, List<EvidenceFragment> evidence) {
		StringBuilder prompt = new StringBuilder(INSTRUCTION).append("\nQuestion: ").append(question)
				.append("\n\nAllowed sources and evidence:\n");
		for (int i = 0; i < evidence.size(); i++) {
			EvidenceFragment fragment = evidence.get(i);
			prompt.append('[').append(i + 1).append("] ").append(fragment.protocol()).append(" / ")
					.append(fragment.section()).append(": ").append(fragment.content()).append('\n');
		}
		return prompt.toString();
	}

}
