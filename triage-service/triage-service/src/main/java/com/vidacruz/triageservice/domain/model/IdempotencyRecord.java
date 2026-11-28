package com.vidacruz.triageservice.domain.model;

/** A stored idempotency key (DEC-15): the canonical request digest and the original response. */
public record IdempotencyRecord(String key, String requestHash, RegistrationResult response) {
}
