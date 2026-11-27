package com.vidacruz.clinical.domain.models;

import com.vidacruz.clinical.domain.exceptions.InvalidPromptException;

/**
 * A clinical question entering the RAG use case.
 *
 * @param question           the question text, stripped; never blank
 * @param topK               how many evidence fragments to retrieve (1..{@value #MAX_TOP_K})
 * @param patientContext     optional free-text context, stripped; blank becomes {@code null}. Validated and carried,
 *                           but NOT forwarded to retrieval or the LLM: whether patient data may reach a model is an
 *                           ADR decision (CA-G-16)
 * @param relevanceThreshold optional minimum relevance in [0, 1]. Validated and applied by {@code RAGService} as the
 *                           per-request relevance gate over the scores returned by the vector store (CA-G-02, ADR-05).
 */
public record PromptRequest(String question, int topK, String patientContext, Double relevanceThreshold) {

	public static final int DEFAULT_TOP_K = 4;

	public static final int MAX_TOP_K = 20;

	public static final int MAX_QUESTION_LENGTH = 2000;

	public static final int MAX_CONTEXT_LENGTH = 2000;

	public PromptRequest {
		if (question == null || question.isBlank()) {
			throw new InvalidPromptException("question must not be blank");
		}
		question = question.strip();
		if (question.length() > MAX_QUESTION_LENGTH) {
			throw new InvalidPromptException("question must not exceed " + MAX_QUESTION_LENGTH + " characters");
		}
		if (topK < 1 || topK > MAX_TOP_K) {
			throw new InvalidPromptException("topK must be between 1 and " + MAX_TOP_K);
		}
		if (patientContext != null) {
			patientContext = patientContext.strip();
			if (patientContext.isEmpty()) {
				patientContext = null;
			}
			else if (patientContext.length() > MAX_CONTEXT_LENGTH) {
				throw new InvalidPromptException(
						"patientContext must not exceed " + MAX_CONTEXT_LENGTH + " characters");
			}
		}
		if (relevanceThreshold != null
				&& (relevanceThreshold.isNaN() || relevanceThreshold < 0.0 || relevanceThreshold > 1.0)) {
			throw new InvalidPromptException("relevanceThreshold must be between 0 and 1");
		}
	}

	public PromptRequest(String question, int topK) {
		this(question, topK, null, null);
	}

	public static PromptRequest of(String question) {
		return new PromptRequest(question, DEFAULT_TOP_K);
	}

	/** Deliberately hides question and context: clinical free text must not reach logs (CA-G-16). */
	@Override
	public String toString() {
		return "PromptRequest[topK=" + topK + "]";
	}

}
