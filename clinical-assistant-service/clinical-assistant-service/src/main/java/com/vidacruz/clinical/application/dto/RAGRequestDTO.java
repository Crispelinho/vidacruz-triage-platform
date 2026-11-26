package com.vidacruz.clinical.application.dto;

import com.vidacruz.clinical.domain.models.PromptRequest;

/** API-04 request body: {@code { "pregunta": "..." }}. */
public record RAGRequestDTO(String pregunta) {

	public PromptRequest toDomain() {
		return PromptRequest.of(pregunta);
	}

	/** Hides the question: clinical free text must not reach logs (CA-G-16). */
	@Override
	public String toString() {
		return "RAGRequestDTO[]";
	}

}
