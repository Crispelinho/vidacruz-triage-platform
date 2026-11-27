package com.vidacruz.clinical.domain.exceptions;

/** A corpus ingestion or retrieval request that fails validation. Messages never echo document or query text. */
public class InvalidCorpusRequestException extends IllegalArgumentException {

	public InvalidCorpusRequestException(String message) {
		super(message);
	}

}
