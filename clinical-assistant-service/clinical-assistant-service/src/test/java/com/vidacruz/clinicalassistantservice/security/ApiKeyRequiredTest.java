package com.vidacruz.clinicalassistantservice.security;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

/** ADR-07: the key has no default, so a missing or blank value must stop startup (fail closed). */
class ApiKeyRequiredTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void rejectsAMissingOrBlankKey(String key) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new ApiKeyAuthenticationFilter(key, new ObjectMapper()))
				.withMessageContaining("must not be blank");
	}
}
