package com.vidacruz.clinical.domain.models;

import java.util.List;

/** Structured LLM output required by G-04: {@code {"respuesta": "...", "fuentes": [{protocolo, seccion}]}}. */
public record GroundedAnswer(String respuesta, List<ProtocolSection> fuentes) {

	public GroundedAnswer {
		if (respuesta == null || respuesta.isBlank()) {
			throw new IllegalArgumentException("respuesta must not be blank");
		}
		if (fuentes == null || fuentes.isEmpty()) {
			throw new IllegalArgumentException("fuentes must contain at least one source");
		}
		fuentes = List.copyOf(fuentes);
	}

}
