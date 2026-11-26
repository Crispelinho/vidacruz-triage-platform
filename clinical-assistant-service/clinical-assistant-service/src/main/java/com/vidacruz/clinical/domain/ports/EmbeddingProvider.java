package com.vidacruz.clinical.domain.ports;

import reactor.core.publisher.Mono;

/** Turns text into an embedding. The model and its dimension are an ADR-05 / G-01 decision. */
public interface EmbeddingProvider {

	Mono<float[]> embed(String text);

}
