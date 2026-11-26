package com.vidacruz.clinical.application.dto;

import java.util.List;

import com.vidacruz.clinical.domain.models.RAGResponse;

/** API-04 response body: {@code { "respuesta": "...", "fuentes": [{ "protocolo": "...", "seccion": "..." }] }}. */
public record RAGResponseDTO(String respuesta, List<FuenteDTO> fuentes) {

	public record FuenteDTO(String protocolo, String seccion) {
	}

	/** Maps to the wire shape: embeddings and chunk text are never exposed, and repeated citations collapse. */
	public static RAGResponseDTO from(RAGResponse response) {
		List<FuenteDTO> fuentes = response.sources().stream()
				.map(fragment -> new FuenteDTO(fragment.protocol(), fragment.section()))
				.distinct()
				.toList();
		return new RAGResponseDTO(response.answer(), fuentes);
	}

}
