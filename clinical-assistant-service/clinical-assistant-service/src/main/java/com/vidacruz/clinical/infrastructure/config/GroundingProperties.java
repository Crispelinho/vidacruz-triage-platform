package com.vidacruz.clinical.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.vidacruz.clinical.domain.services.GroundingCheck;

/**
 * Post-check policy for the answer text (M-1), bound from {@code rag.grounding}. Every answer needs a minimum lexical
 * overlap with its cited evidence, whether or not it carries an {@code [n]} marker. Strict markers, a higher overlap
 * and the numeric check are opt-in because the Ollama fixture answer cannot satisfy them.
 */
@ConfigurationProperties(prefix = "rag.grounding")
public record GroundingProperties(@DefaultValue("false") boolean requireMarkers,
		@DefaultValue("0.2") double markerlessMinOverlap, @DefaultValue("0.2") double minLexicalOverlap,
		@DefaultValue("false") boolean numbersMustAppearInEvidence) {

	public GroundingCheck.Policy toPolicy() {
		return new GroundingCheck.Policy(requireMarkers, markerlessMinOverlap, minLexicalOverlap,
				numbersMustAppearInEvidence);
	}

}
