package com.vidacruz.clinical.application.dto;

import com.vidacruz.clinical.domain.models.RetrievedChunk;

/** One element of {@code GET /clinical/corpus/retrieve}. Embeddings are never exposed. */
public record RetrievedChunkDTO(String text, double similarity, String source) {

	public static RetrievedChunkDTO from(RetrievedChunk chunk) {
		return new RetrievedChunkDTO(chunk.text(), chunk.similarity(), chunk.source());
	}

}
