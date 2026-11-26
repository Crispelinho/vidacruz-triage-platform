package com.vidacruz.clinical.domain.services;

import java.util.List;

import com.vidacruz.clinical.domain.models.EvidenceFragment;

/**
 * Renders retrieved evidence as the numbered context an LLM receives, so the answer can cite {@code [n]} markers that
 * {@link CitationValidator} later resolves. Pure and provider-agnostic: how the context is placed in a prompt belongs
 * to the real LLM adapter (ADR-05).
 */
public final class EvidenceContext {

	private EvidenceContext() {
	}

	public static String render(List<EvidenceFragment> evidence) {
		StringBuilder context = new StringBuilder();
		for (int i = 0; i < evidence.size(); i++) {
			EvidenceFragment fragment = evidence.get(i);
			context.append('[').append(i + 1).append("] ");
			if (fragment.protocol() != null) {
				context.append(fragment.protocol());
			}
			if (fragment.section() != null) {
				context.append(" / ").append(fragment.section());
			}
			context.append('\n').append(fragment.content()).append("\n\n");
		}
		return context.toString().stripTrailing();
	}

}
