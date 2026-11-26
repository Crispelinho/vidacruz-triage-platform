package com.vidacruz.clinical.domain.services;

import com.vidacruz.clinical.domain.model.urgency.UrgencyState;
import com.vidacruz.clinical.domain.ports.UrgencyStateRepository;
import reactor.core.publisher.Mono;

/**
 * RF-04B: active patients per level, read from the projection, never from the triage API.
 */
public class GetUrgencyStateUseCase {

    private final UrgencyStateRepository urgencyStateRepository;

    public GetUrgencyStateUseCase(UrgencyStateRepository urgencyStateRepository) {
        this.urgencyStateRepository = urgencyStateRepository;
    }

    public Mono<UrgencyState> current() {
        return urgencyStateRepository.current();
    }
}
