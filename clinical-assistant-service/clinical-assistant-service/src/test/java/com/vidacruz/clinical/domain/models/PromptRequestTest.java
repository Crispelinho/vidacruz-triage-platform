package com.vidacruz.clinical.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.vidacruz.clinical.domain.exceptions.InvalidPromptException;

class PromptRequestTest {

	@Test
	void ofUsesDefaultTopKAndStripsQuestion() {
		PromptRequest request = PromptRequest.of("  medidas iniciales?  ");

		assertThat(request.question()).isEqualTo("medidas iniciales?");
		assertThat(request.topK()).isEqualTo(PromptRequest.DEFAULT_TOP_K);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " ", "\t\n" })
	void rejectsBlankQuestion(String question) {
		assertThatThrownBy(() -> PromptRequest.of(question)).isInstanceOf(InvalidPromptException.class)
				.hasMessageContaining("question");
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, PromptRequest.MAX_TOP_K + 1 })
	void rejectsTopKOutOfRange(int topK) {
		assertThatThrownBy(() -> new PromptRequest("q", topK)).isInstanceOf(InvalidPromptException.class)
				.hasMessageContaining("topK");
	}

	@Test
	void acceptsTopKBounds() {
		assertThat(new PromptRequest("q", 1).topK()).isEqualTo(1);
		assertThat(new PromptRequest("q", PromptRequest.MAX_TOP_K).topK()).isEqualTo(PromptRequest.MAX_TOP_K);
	}

	@Test
	void toStringDoesNotLeakTheQuestion() {
		assertThat(PromptRequest.of("dolor toracico en paciente X").toString()).doesNotContain("dolor");
	}

}
