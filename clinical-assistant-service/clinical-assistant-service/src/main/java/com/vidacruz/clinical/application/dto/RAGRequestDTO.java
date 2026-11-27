package com.vidacruz.clinical.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vidacruz.clinical.domain.models.PromptRequest;

/**
 * Request body: {@code { "pregunta": "...", "contexto_paciente": "...", "umbral_relevancia": 0.5 }}. API-04 defines
 * only {@code pregunta}; the other two are optional, validated, and not yet acted on (see {@link PromptRequest}).
 */
public record RAGRequestDTO(@JsonProperty("pregunta") String question,
		@JsonProperty("contexto_paciente") String patientContext,
		@JsonProperty("umbral_relevancia") Double relevanceThreshold) {

	public RAGRequestDTO(String question) {
		this(question, null, null);
	}

	public PromptRequest toDomain() {
		return new PromptRequest(question, PromptRequest.DEFAULT_TOP_K, patientContext, relevanceThreshold);
	}

	/** Hides question and context: clinical free text must not reach logs (CA-G-16). */
	@Override
	public String toString() {
		return "RAGRequestDTO[]";
	}

}
