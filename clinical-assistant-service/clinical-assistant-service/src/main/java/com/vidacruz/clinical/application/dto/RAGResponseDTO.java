package com.vidacruz.clinical.application.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vidacruz.clinical.domain.models.Citation;
import com.vidacruz.clinical.domain.models.RAGResponse;

/**
 * Response body: {@code { "respuesta": "...", "fuentes": [...], "citas": [...], "modelo_usado": "node/model" }}.
 * The {@code @JsonProperty} values are the API wire keys; Java accessors are English. {@code fuentes} is the API-04
 * shape. {@code citas} is additive provenance: one entry per retrieved fragment, numbered like the {@code [n]}
 * markers in the answer. {@code modelo_usado} is the exact node/model that answered, or {@code none} when no LLM
 * was called. There is deliberately no confidence field: the LLM provides no calibrated clinical probability.
 */
public record RAGResponseDTO(
		@JsonProperty("respuesta") String answer,
		@JsonProperty("fuentes") List<SourceDTO> sources,
		@JsonProperty("citas") List<CitationDTO> citations,
		@JsonProperty("modelo_usado") String usedModel) {

	public record SourceDTO(@JsonProperty("protocolo") String protocol, @JsonProperty("seccion") String section) {
	}

	public record CitationDTO(
			@JsonProperty("posicion") int position,
			@JsonProperty("fragmentoId") UUID fragmentId,
			@JsonProperty("documentoId") UUID documentId,
			@JsonProperty("indiceFragmento") int chunkIndex,
			@JsonProperty("protocolo") String protocol,
			@JsonProperty("seccion") String section) {

		static CitationDTO from(Citation citation) {
			return new CitationDTO(citation.position(), citation.fragmentId(), citation.documentId(),
					citation.chunkIndex(), citation.protocol(), citation.section());
		}

	}

	/** Maps to the wire shape: embeddings and chunk text are never exposed, and repeated sources collapse. */
	public static RAGResponseDTO from(RAGResponse response) {
		List<SourceDTO> sources = response.sources().stream()
				.map(fragment -> new SourceDTO(fragment.protocol(), fragment.section()))
				.distinct()
				.toList();
		List<CitationDTO> citations = response.citations().stream().map(CitationDTO::from).toList();
		return new RAGResponseDTO(response.answer(), sources, citations, response.usedModel());
	}

}
