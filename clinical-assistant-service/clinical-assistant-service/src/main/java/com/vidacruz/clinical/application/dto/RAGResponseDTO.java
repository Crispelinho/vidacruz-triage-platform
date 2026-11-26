package com.vidacruz.clinical.application.dto;

import java.util.List;
import java.util.UUID;

import com.vidacruz.clinical.domain.models.Citation;
import com.vidacruz.clinical.domain.models.RAGResponse;

/**
 * Response body: {@code { "respuesta": "...", "fuentes": [...], "citas": [...], "modelo_usado": "node/model" }}.
 * {@code fuentes} is the API-04 shape. {@code citas} is additive provenance: one entry per retrieved fragment,
 * numbered like the {@code [n]} markers in the answer. {@code modelo_usado} is the exact node/model that answered,
 * or {@code none} when no LLM was called. There is deliberately no confidence field: the LLM provides no calibrated
 * clinical probability.
 */
public record RAGResponseDTO(String respuesta, List<FuenteDTO> fuentes, List<CitaDTO> citas, String modelo_usado) {

	public record FuenteDTO(String protocolo, String seccion) {
	}

	public record CitaDTO(int posicion, UUID fragmentoId, UUID documentoId, int indiceFragmento, String protocolo,
			String seccion) {

		static CitaDTO from(Citation citation) {
			return new CitaDTO(citation.position(), citation.fragmentId(), citation.documentId(),
					citation.chunkIndex(), citation.protocol(), citation.section());
		}

	}

	/** Maps to the wire shape: embeddings and chunk text are never exposed, and repeated sources collapse. */
	public static RAGResponseDTO from(RAGResponse response) {
		List<FuenteDTO> fuentes = response.sources().stream()
				.map(fragment -> new FuenteDTO(fragment.protocol(), fragment.section()))
				.distinct()
				.toList();
		List<CitaDTO> citas = response.citations().stream().map(CitaDTO::from).toList();
		return new RAGResponseDTO(response.answer(), fuentes, citas, response.modelo_usado());
	}

}
