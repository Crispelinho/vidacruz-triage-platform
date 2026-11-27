package com.vidacruz.clinical.domain.ports;

import java.util.List;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.LLMCompletion;

import reactor.core.publisher.Mono;

/**
 * Ordered, resilient LLM call. The router owns failover between nodes; a single provider is still an
 * {@link LLMProvider}. A router failure surfaces as {@code LLMUnavailableException}.
 */
public interface LLMRouter {

	Mono<LLMCompletion> generate(String question, List<EvidenceFragment> evidence);

}
