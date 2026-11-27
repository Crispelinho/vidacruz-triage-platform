package com.vidacruz.clinical.domain.ports;

import reactor.core.publisher.Mono;

/**
 * Optional capability for embedding providers whose model distinguishes index text from query text (ADR-05).
 *
 * <p>Providers that do not implement this interface keep the single {@link EmbeddingProvider#embed(String)} path;
 * callers use the task-aware methods only when the selected provider supports them.
 */
public interface TaskAwareEmbeddingProvider extends EmbeddingProvider {

	Mono<float[]> embedQuery(String text);

	Mono<float[]> embedDocument(String text);

}
