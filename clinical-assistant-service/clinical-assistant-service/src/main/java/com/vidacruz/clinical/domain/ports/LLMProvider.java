package com.vidacruz.clinical.domain.ports;

import java.util.List;

import com.vidacruz.clinical.domain.models.EvidenceFragment;

import reactor.core.publisher.Mono;

/** Generates an answer grounded on the given evidence. Failures surface as {@code LLMUnavailableException}. */
public interface LLMProvider {

	Mono<String> generate(String question, List<EvidenceFragment> evidence);

}
