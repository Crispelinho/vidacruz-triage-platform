package com.vidacruz.clinical.domain.ports;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import com.vidacruz.clinical.domain.models.IngestedDocument;

import reactor.core.publisher.Mono;

/** Registry of ingested corpus documents. Chunks and embeddings live in the {@link VectorStore}. */
public interface ClinicalDocumentRepository {

	/** Emits the document, or completes empty when it was never ingested. */
	Mono<IngestedDocument> findById(UUID id);

	/** Stores the document; a no-op when the id already exists. Emits the stored entry either way. */
	Mono<IngestedDocument> saveIfAbsent(IngestedDocument document, String content);

	/** Titles by document id. Ids without a registry row (e.g. seeded protocols) are simply absent from the map. */
	Mono<Map<UUID, String>> findTitles(Collection<UUID> ids);

}
