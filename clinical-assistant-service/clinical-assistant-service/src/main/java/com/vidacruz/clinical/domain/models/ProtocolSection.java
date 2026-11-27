package com.vidacruz.clinical.domain.models;

import java.util.Objects;

/**
 * Corpus key of a citation, decided under G-03: a protocol code and one of its sections.
 *
 * @param protocolo protocol code, e.g. {@code PROTO-001}
 * @param seccion   section title, e.g. {@code Medidas iniciales}
 */
public record ProtocolSection(String protocolo, String seccion) {

	public ProtocolSection {
		protocolo = requireText(protocolo, "protocolo");
		seccion = requireText(seccion, "seccion");
	}

	public static ProtocolSection of(String protocolo, String seccion) {
		return new ProtocolSection(protocolo, seccion);
	}

	public static ProtocolSection from(EvidenceFragment fragment) {
		return new ProtocolSection(fragment.protocol(), fragment.section());
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return value.trim();
	}

	@Override
	public String toString() {
		return Objects.toString(protocolo, "") + " / " + Objects.toString(seccion, "");
	}

}
