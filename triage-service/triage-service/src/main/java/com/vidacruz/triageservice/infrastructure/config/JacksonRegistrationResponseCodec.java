package com.vidacruz.triageservice.infrastructure.config;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.domain.dto.PatientRegistrationResponse;
import com.vidacruz.triageservice.domain.port.RegistrationResponseCodec;

/** Serializes the stored idempotency response using the application wire mixins. */
@Component
public class JacksonRegistrationResponseCodec implements RegistrationResponseCodec {

	private final ObjectMapper objectMapper;

	public JacksonRegistrationResponseCodec(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public String encode(PatientRegistrationResponse response) {
		try {
			return objectMapper.writeValueAsString(response);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Cannot store idempotency response", exception);
		}
	}

	@Override
	public PatientRegistrationResponse decode(String body) {
		try {
			return objectMapper.readValue(body, PatientRegistrationResponse.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Stored idempotency response is not readable", exception);
		}
	}
}
