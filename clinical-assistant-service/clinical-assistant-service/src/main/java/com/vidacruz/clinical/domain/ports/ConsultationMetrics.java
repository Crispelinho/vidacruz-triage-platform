package com.vidacruz.clinical.domain.ports;

/**
 * How a clinical question ended, for NFR-07. The port carries no question, answer, patient id or clinical text: only
 * the coarse outcome of the request.
 */
public interface ConsultationMetrics {

	/** A grounded answer was returned. */
	void answered();

	/** The fixed notice was returned because no evidence reached the relevance gate. */
	void noEvidence();

	/** The model answer was discarded because it cited evidence outside the retrieved set. */
	void invalidCitation();

	/** The answer could not be produced and the API returned the degraded-mode 503. */
	void unavailable();
}
