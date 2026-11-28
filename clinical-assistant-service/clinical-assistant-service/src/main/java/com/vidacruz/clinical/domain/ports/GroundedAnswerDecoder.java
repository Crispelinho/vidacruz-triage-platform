package com.vidacruz.clinical.domain.ports;

import com.vidacruz.clinical.domain.exceptions.InvalidLLMResponseException;
import com.vidacruz.clinical.domain.models.GroundedAnswer;

/**
 * Decodes the raw LLM output into the G-04 contract:
 * {@code {"respuesta":"...","fuentes":[{"protocolo":"...","seccion":"..."}]}}.
 *
 * <p>The wire format (JSON) is an infrastructure concern, so the domain only sees this port. Implementations must be
 * strict: any deviation from the contract throws {@link InvalidLLMResponseException}, and the message must never echo
 * the raw answer (it may contain patient data). The caller rejects the answer and never regenerates it, which is why
 * decoding happens in the use case and not inside the LLM router: a decode failure must not trigger a provider
 * failover.
 */
@FunctionalInterface
public interface GroundedAnswerDecoder {

	GroundedAnswer decode(String raw);

}
