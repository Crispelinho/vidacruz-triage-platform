package com.vidacruz.clinical.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.vidacruz.clinical.domain.services.GroundingCheck;

/**
 * Post-check policy for the answer text (M-1), bound from {@code rag.grounding}. Markers are required by default; the
 * lexical-overlap and numeric checks are opt-in because the Ollama fixture answer cannot satisfy them.
 */
@ConfigurationProperties(prefix = "rag.grounding")
public record GroundingProperties(@DefaultValue("true") boolean requireMarkers,
		@DefaultValue("0.0") double minLexicalOverlap, @DefaultValue("false") boolean numbersMustAppearInEvidence) {

	public GroundingCheck.Policy toPolicy() {
		return new GroundingCheck.Policy(requireMarkers, minLexicalOverlap, numbersMustAppearInEvidence);
	}

}
