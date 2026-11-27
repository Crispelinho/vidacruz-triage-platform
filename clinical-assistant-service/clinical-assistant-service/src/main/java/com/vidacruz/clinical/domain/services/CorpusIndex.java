package com.vidacruz.clinical.domain.services;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.vidacruz.clinical.domain.models.ProtocolSection;

/**
 * In-memory set of {@code (protocolo, seccion)} pairs present in the clinical corpus (G-03). Loaded at startup from
 * the vector store and refreshed on ingestion; no {@code DocumentoClinico} read is performed per request.
 */
public final class CorpusIndex {

	private final Set<ProtocolSection> entries = ConcurrentHashMap.newKeySet();

	public void add(ProtocolSection pair) {
		entries.add(Objects.requireNonNull(pair, "pair"));
	}

	public void refresh(Collection<ProtocolSection> pairs) {
		Objects.requireNonNull(pairs, "pairs");
		entries.clear();
		entries.addAll(pairs);
	}

	public boolean contains(ProtocolSection pair) {
		return pair != null && entries.contains(pair);
	}

	public int size() {
		return entries.size();
	}

}
