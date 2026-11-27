package com.vidacruz.clinical.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.exceptions.InvalidLLMResponseException;
import com.vidacruz.clinical.domain.models.GroundedAnswer;

class GroundedAnswerParserTest {

	@Test
	void parsesTheG04Contract() {
		GroundedAnswer answer = GroundedAnswerParser.parse("{\"respuesta\":\"texto [1]\",\"fuentes\":["
				+ "{\"protocolo\":\"PROTO-001\",\"seccion\":\"Medidas iniciales\"}]}");

		assertThat(answer.respuesta()).isEqualTo("texto [1]");
		assertThat(answer.fuentes()).hasSize(1);
		assertThat(answer.fuentes().get(0).protocolo()).isEqualTo("PROTO-001");
	}

	@Test
	void rejectsMalformedJsonWithoutEchoingContent() {
		assertThatThrownBy(() -> GroundedAnswerParser.parse("respuesta libre con dato sensible"))
				.isInstanceOf(InvalidLLMResponseException.class)
				.hasMessageNotContaining("dato sensible");
	}

	@Test
	void rejectsWrongShapesAndEmptySources() {
		assertThatThrownBy(() -> GroundedAnswerParser.parse("[]")).isInstanceOf(InvalidLLMResponseException.class);
		assertThatThrownBy(() -> GroundedAnswerParser.parse("{\"respuesta\":\"x\"}"))
				.isInstanceOf(InvalidLLMResponseException.class);
		assertThatThrownBy(() -> GroundedAnswerParser.parse("{\"respuesta\":\"x\",\"fuentes\":[]}"))
				.isInstanceOf(InvalidLLMResponseException.class);
		assertThatThrownBy(() -> GroundedAnswerParser.parse(
				"{\"respuesta\":\"x\",\"fuentes\":[{\"protocolo\":\"P\",\"seccion\":\" \"}]}"))
				.isInstanceOf(InvalidLLMResponseException.class);
	}

}
