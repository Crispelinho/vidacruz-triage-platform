package com.vidacruz.clinical.domain.services;

import com.vidacruz.clinical.domain.model.urgency.PatientSnapshot;
import com.vidacruz.clinical.domain.ports.UrgencyStateRepository;
import reactor.core.publisher.Mono;

/**
 * RF-04B: keeps the projection up to date with every triage event and decides whether the topic must be replayed.
 */
public class ProjectTriageEventUseCase {

    private final UrgencyStateRepository urgencyStateRepository;

    public ProjectTriageEventUseCase(UrgencyStateRepository urgencyStateRepository) {
        this.urgencyStateRepository = urgencyStateRepository;
    }

    /** True when the event changed the projection, false when it was a duplicate or older. */
    public Mono<Boolean> project(PatientSnapshot snapshot) {
        return urgencyStateRepository.apply(snapshot);
    }

    /** True when the projection is empty and has to be rebuilt from the first event of the topic. */
    public Mono<Boolean> needsRebuild() {
        return urgencyStateRepository.isInitialized().map(initialized -> !initialized);
    }

    /** Called once the consumer reads from the right position, so a later start does not rebuild again. */
    public Mono<Void> rebuildStarted() {
        return urgencyStateRepository.markInitialized();
    }
}
