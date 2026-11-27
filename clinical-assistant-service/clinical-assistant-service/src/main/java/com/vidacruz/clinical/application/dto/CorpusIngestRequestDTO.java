package com.vidacruz.clinical.application.dto;

/** Request body of {@code POST /clinical/corpus/ingest}. Validation lives in the domain service. */
public record CorpusIngestRequestDTO(String title, String content) {

	/** Hides the document: clinical free text must not reach logs (CA-G-16). */
	@Override
	public String toString() {
		return "CorpusIngestRequestDTO[]";
	}

}
