package com.vidacruz.clinical.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.vidacruz.clinical.domain.exceptions.InvalidPromptException;

class PromptRequestOptionalFieldsTest {

	@Test
	void optionalFieldsDefaultToNull() {
		PromptRequest request = PromptRequest.of("q");

		assertThat(request.patientContext()).isNull();
		assertThat(request.relevanceThreshold()).isNull();
	}

	@Test
	void stripsContextAndTreatsBlankAsAbsent() {
		assertThat(new PromptRequest("q", 4, "  antecedentes  ", null).patientContext()).isEqualTo("antecedentes");
		assertThat(new PromptRequest("q", 4, "   ", null).patientContext()).isNull();
	}

	@Test
	void rejectsOversizedQuestionAndContext() {
		String tooLong = "x".repeat(PromptRequest.MAX_QUESTION_LENGTH + 1);
		assertThatThrownBy(() -> PromptRequest.of(tooLong)).isInstanceOf(InvalidPromptException.class)
				.hasMessageContaining("question");
		String longContext = "x".repeat(PromptRequest.MAX_CONTEXT_LENGTH + 1);
		assertThatThrownBy(() -> new PromptRequest("q", 4, longContext, null))
				.isInstanceOf(InvalidPromptException.class)
				.hasMessageContaining("patientContext");
	}

	@Test
	void acceptsMaximumLengths() {
		assertThat(PromptRequest.of("x".repeat(PromptRequest.MAX_QUESTION_LENGTH)).question()).hasSize(2000);
		assertThat(new PromptRequest("q", 4, "x".repeat(PromptRequest.MAX_CONTEXT_LENGTH), null).patientContext())
				.hasSize(2000);
	}

	@ParameterizedTest
	@ValueSource(doubles = { -0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY })
	void rejectsThresholdOutsideZeroToOne(double threshold) {
		assertThatThrownBy(() -> new PromptRequest("q", 4, null, threshold)).isInstanceOf(InvalidPromptException.class)
				.hasMessageContaining("relevanceThreshold");
	}

	@ParameterizedTest
	@ValueSource(doubles = { 0.0, 0.5, 1.0 })
	void acceptsThresholdBounds(double threshold) {
		assertThat(new PromptRequest("q", 4, null, threshold).relevanceThreshold()).isEqualTo(threshold);
	}

	@Test
	void toStringLeaksNeitherQuestionNorContext() {
		String text = new PromptRequest("dolor toracico", 4, "paciente con diabetes", 0.5).toString();

		assertThat(text).doesNotContain("dolor").doesNotContain("diabetes");
	}

}
