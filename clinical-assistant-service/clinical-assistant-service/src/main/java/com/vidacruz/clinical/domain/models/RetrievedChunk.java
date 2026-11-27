package com.vidacruz.clinical.domain.models;

import java.util.Objects;
import java.util.UUID;

/**
 * A chunk that passed the RN-05 relevance gate.
 *
 * @param similarity cosine similarity in [0, 1]
 * @param source     human-readable origin: document title, else protocol code, else the document id
 */
public record RetrievedChunk(UUID documentId, int chunkIndex, String text, double similarity, String source) {

	public RetrievedChunk {
		Objects.requireNonNull(documentId, "documentId");
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(source, "source");
	}

}
