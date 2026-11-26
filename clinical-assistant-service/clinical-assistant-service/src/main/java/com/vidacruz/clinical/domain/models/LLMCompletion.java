package com.vidacruz.clinical.domain.models;

import java.util.Objects;

/**
 * Answer returned by one LLM node, carrying the provenance needed by {@code usedModel}.
 *
 * @param provider node key from the configured chain, for example {@code ollama}
 * @param model model identifier used by that node, for example {@code llama3.1:8b}
 * @param answer generated answer; question and evidence text stay out of logs (CA-G-16)
 */
public record LLMCompletion(String provider, String model, String answer) {

	public LLMCompletion {
		if (provider == null || provider.isBlank()) {
			throw new IllegalArgumentException("provider must not be blank");
		}
		if (model == null || model.isBlank()) {
			throw new IllegalArgumentException("model must not be blank");
		}
		answer = Objects.requireNonNull(answer, "answer");
	}

	/** Wire value for the {@code modelo_usado} response key. */
	public String usedModel() {
		return provider + "/" + model;
	}

}
