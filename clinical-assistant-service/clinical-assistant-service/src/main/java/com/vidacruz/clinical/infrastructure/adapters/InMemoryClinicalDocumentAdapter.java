package com.vidacruz.clinical.infrastructure.adapters;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.vidacruz.clinical.domain.models.IngestedDocument;
import com.vidacruz.clinical.domain.ports.ClinicalDocumentRepository;

import reactor.core.publisher.Mono;

/** In-memory registry for {@code clinical.vector-store.mode=fixture}: no database, pipeline tests only. */
public class InMemoryClinicalDocumentAdapter implements ClinicalDocumentRepository {

	private final Map<UUID, IngestedDocument> documents = new ConcurrentHashMap<>();

	@Override
	public Mono<IngestedDocument> findById(UUID id) {
		return Mono.justOrEmpty(documents.get(id));
	}

	@Override
	public Mono<IngestedDocument> saveIfAbsent(IngestedDocument document, String content) {
		return Mono.just(documents.computeIfAbsent(document.documentId(), id -> document));
	}

	@Override
	public Mono<Map<UUID, String>> findTitles(Collection<UUID> ids) {
		return Mono.just(ids.stream()
				.filter(documents::containsKey)
				.collect(Collectors.toMap(id -> id, id -> documents.get(id).title())));
	}

}
