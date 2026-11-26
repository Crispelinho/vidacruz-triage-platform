package com.vidacruz.clinical.domain.ports;

import com.vidacruz.clinical.domain.model.urgency.PatientSnapshot;
import com.vidacruz.clinical.domain.model.urgency.UrgencyState;
import reactor.core.publisher.Mono;

/**
 * Projection of the emergency room built from the events of the triage.
 */
public interface UrgencyStateRepository {

    /** Applies a patient state atomically; false when an equal or newer state was already stored. */
    Mono<Boolean> apply(PatientSnapshot snapshot);

    Mono<UrgencyState> current();

    /** False when the projection was never built: the events must be read from the start. */
    Mono<Boolean> isInitialized();

    Mono<Void> markInitialized();
}
