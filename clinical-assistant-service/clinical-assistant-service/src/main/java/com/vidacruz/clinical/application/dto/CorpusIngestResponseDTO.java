package com.vidacruz.clinical.application.dto;

import java.util.UUID;

import com.vidacruz.clinical.domain.models.IngestedDocument;

/** Response of {@code POST /clinical/corpus/ingest}: {@code {"documentId": "...", "chunksCount": 3}}. */
public record CorpusIngestResponseDTO(UUID documentId, int chunksCount) {

	public static CorpusIngestResponseDTO from(IngestedDocument document) {
		return new CorpusIngestResponseDTO(document.documentId(), document.chunksCount());
	}

}
