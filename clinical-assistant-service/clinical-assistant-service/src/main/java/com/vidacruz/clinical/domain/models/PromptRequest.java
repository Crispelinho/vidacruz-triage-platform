package com.vidacruz.clinical.domain.models;

import com.vidacruz.clinical.domain.exceptions.InvalidPromptException;

/**
 * A clinical question entering the RAG use case.
 *
 * @param question the question text, stripped; never blank
 * @param topK     how many evidence fragments to retrieve (1..{@value #MAX_TOP_K})
 */
public record PromptRequest(String question, int topK) {

	public static final int DEFAULT_TOP_K = 4;

	public static final int MAX_TOP_K = 20;

	public PromptRequest {
		if (question == null || question.isBlank()) {
			throw new InvalidPromptException("question must not be blank");
		}
		if (topK < 1 || topK > MAX_TOP_K) {
			throw new InvalidPromptException("topK must be between 1 and " + MAX_TOP_K);
		}
		question = question.strip();
	}

	public static PromptRequest of(String question) {
		return new PromptRequest(question, DEFAULT_TOP_K);
	}

	/** Deliberately hides the question: clinical free text must not reach logs (CA-G-16). */
	@Override
	public String toString() {
		return "PromptRequest[topK=" + topK + "]";
	}

}
