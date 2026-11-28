package com.vidacruz.triageservice.domain.model;

/** Digest of the canonical, validated registration body used to detect idempotency-key reuse (DEC-15, ADR-12). */
public interface RequestFingerprinter {

	String fingerprint(PatientRegistration registration);
}
