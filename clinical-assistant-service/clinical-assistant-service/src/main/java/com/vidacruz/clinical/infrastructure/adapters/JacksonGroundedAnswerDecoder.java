package com.vidacruz.clinical.infrastructure.adapters;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.clinical.domain.exceptions.InvalidLLMResponseException;
import com.vidacruz.clinical.domain.models.GroundedAnswer;
import com.vidacruz.clinical.domain.models.ProtocolSection;
import com.vidacruz.clinical.domain.ports.GroundedAnswerDecoder;

/**
 * Strict Jackson decoder for the G-04 LLM contract:
 * {@code {"respuesta":"...","fuentes":[{"protocolo":"...","seccion":"..."}]}}. Any parse error throws
 * {@link InvalidLLMResponseException}; the caller rejects the answer without regenerating it.
 */
public final class JacksonGroundedAnswerDecoder implements GroundedAnswerDecoder {

	private final ObjectMapper mapper;

	public JacksonGroundedAnswerDecoder() {
		this(new ObjectMapper());
	}

	public JacksonGroundedAnswerDecoder(ObjectMapper mapper) {
		this.mapper = Objects.requireNonNull(mapper, "mapper");
	}

	@Override
	public GroundedAnswer decode(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new InvalidLLMResponseException("LLM answer is empty");
		}
		JsonNode root;
		try {
			root = mapper.readTree(unwrapMarkdownJson(raw));
		}
		catch (JsonProcessingException ex) {
			throw new InvalidLLMResponseException("LLM answer is not valid JSON", ex);
		}
		if (root == null || !root.isObject() || !hasText(root, "respuesta") || !root.has("fuentes")
				|| !root.get("fuentes").isArray()) {
			throw new InvalidLLMResponseException("LLM answer must be an object with respuesta and fuentes");
		}
		String respuesta = root.get("respuesta").asText();
		if (respuesta.isBlank()) {
			throw new InvalidLLMResponseException("LLM answer respuesta is blank");
		}
		List<ProtocolSection> fuentes = new ArrayList<>();
		for (JsonNode node : root.get("fuentes")) {
			if (!node.isObject() || !hasText(node, "protocolo") || !hasText(node, "seccion")) {
				throw new InvalidLLMResponseException("LLM answer fuentes need protocolo and seccion");
			}
			try {
				fuentes.add(ProtocolSection.of(node.get("protocolo").asText(), node.get("seccion").asText()));
			}
			catch (IllegalArgumentException ex) {
				throw new InvalidLLMResponseException("LLM answer fuentes contain blank metadata", ex);
			}
		}
		if (fuentes.isEmpty()) {
			throw new InvalidLLMResponseException("LLM answer has no fuentes");
		}
		return new GroundedAnswer(respuesta, fuentes);
	}

	/**
	 * Real chat models often wrap the requested JSON in a markdown code fence even when prompted for JSON only. Remove
	 * one such wrapper before parsing; the G-04 contract itself stays strict. Anything else is left untouched so that
	 * malformed answers are still rejected.
	 */
	private static String unwrapMarkdownJson(String raw) {
		String trimmed = raw.strip();
		if (!trimmed.startsWith("```") || !trimmed.endsWith("```") || trimmed.length() < 6) {
			return raw;
		}
		int newline = trimmed.indexOf('\n');
		if (newline < 0) {
			return raw;
		}
		return trimmed.substring(newline + 1, trimmed.length() - 3).strip();
	}

	private static boolean hasText(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value != null && value.isTextual() && !value.asText().isBlank();
	}

}
