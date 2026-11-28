package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.exceptions.InvalidLLMResponseException;
import com.vidacruz.clinical.domain.models.GroundedAnswer;

class JacksonGroundedAnswerDecoderTest {

	private final JacksonGroundedAnswerDecoder decoder = new JacksonGroundedAnswerDecoder();

	@Test
	void decodesTheG04Contract() {
		GroundedAnswer answer = decoder.decode("{\"respuesta\":\"texto [1]\",\"fuentes\":["
				+ "{\"protocolo\":\"PROTO-001\",\"seccion\":\"Medidas iniciales\"}]}");

		assertThat(answer.respuesta()).isEqualTo("texto [1]");
		assertThat(answer.fuentes()).hasSize(1);
		assertThat(answer.fuentes().get(0).protocolo()).isEqualTo("PROTO-001");
	}

	@Test
	void decodesFencedJsonWithLanguageTag() {
		GroundedAnswer answer = decoder.decode("```json\n{\"respuesta\":\"texto [1]\",\"fuentes\":["
				+ "{\"protocolo\":\"PROTO-001\",\"seccion\":\"Medidas iniciales\"}]}\n```");

		assertThat(answer.respuesta()).isEqualTo("texto [1]");
		assertThat(answer.fuentes()).hasSize(1);
		assertThat(answer.fuentes().get(0).protocolo()).isEqualTo("PROTO-001");
	}

	@Test
	void decodesFencedJsonWithoutLanguageTag() {
		GroundedAnswer answer = decoder.decode("```\n{\"respuesta\":\"otro texto\",\"fuentes\":["
				+ "{\"protocolo\":\"PROTO-002\",\"seccion\":\"Signos\"}]}\n```");

		assertThat(answer.respuesta()).isEqualTo("otro texto");
		assertThat(answer.fuentes().get(0).protocolo()).isEqualTo("PROTO-002");
	}

	@Test
	void keepsStrictContractForFencedJson() {
		assertThatThrownBy(() -> decoder.decode("```json\n{\"respuesta\":\"x\",\"fuentes\":[]}\n```"))
				.isInstanceOf(InvalidLLMResponseException.class);
		assertThatThrownBy(() -> decoder.decode("```json\n{\"respuesta\":\"x\"}\n```"))
				.isInstanceOf(InvalidLLMResponseException.class);
	}

	@Test
	void rejectsMalformedJsonWithoutEchoingContent() {
		assertThatThrownBy(() -> decoder.decode("respuesta libre con dato sensible"))
				.isInstanceOf(InvalidLLMResponseException.class)
				.hasMessageNotContaining("dato sensible");
	}

	@Test
	void rejectsEmptyInput() {
		assertThatThrownBy(() -> decoder.decode(null)).isInstanceOf(InvalidLLMResponseException.class);
		assertThatThrownBy(() -> decoder.decode("  ")).isInstanceOf(InvalidLLMResponseException.class);
	}

	@Test
	void rejectsWrongShapesAndEmptySources() {
		assertThatThrownBy(() -> decoder.decode("[]")).isInstanceOf(InvalidLLMResponseException.class);
		assertThatThrownBy(() -> decoder.decode("{\"respuesta\":\"x\"}"))
				.isInstanceOf(InvalidLLMResponseException.class);
		assertThatThrownBy(() -> decoder.decode("{\"respuesta\":\"x\",\"fuentes\":[]}"))
				.isInstanceOf(InvalidLLMResponseException.class);
		assertThatThrownBy(() -> decoder.decode(
				"{\"respuesta\":\"x\",\"fuentes\":[{\"protocolo\":\"P\",\"seccion\":\" \"}]}"))
				.isInstanceOf(InvalidLLMResponseException.class);
	}

}
