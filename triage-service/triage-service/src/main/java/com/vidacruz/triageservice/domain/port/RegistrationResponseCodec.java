package com.vidacruz.triageservice.domain.port;

import com.vidacruz.triageservice.domain.dto.PatientRegistrationResponse;

/** Serialization boundary for the response stored with an idempotency key. */
public interface RegistrationResponseCodec {

	String encode(PatientRegistrationResponse response);

	PatientRegistrationResponse decode(String body);
}
