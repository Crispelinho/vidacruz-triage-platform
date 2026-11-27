package com.vidacruz.clinical.domain.models;

import java.util.Objects;
import java.util.UUID;

/** Registry entry of an ingested corpus document. Content stays in storage and out of this value. */
public record IngestedDocument(UUID documentId, String title, int chunksCount) {

	public IngestedDocument {
		Objects.requireNonNull(documentId, "documentId");
		Objects.requireNonNull(title, "title");
		if (chunksCount < 1) {
			throw new IllegalArgumentException("chunksCount must be positive");
		}
	}

}
