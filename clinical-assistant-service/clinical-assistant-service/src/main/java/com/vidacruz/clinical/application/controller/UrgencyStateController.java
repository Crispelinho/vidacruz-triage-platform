package com.vidacruz.clinical.application.controller;

import com.vidacruz.clinical.application.dto.UrgencyStateResponse;
import com.vidacruz.clinical.domain.model.urgency.TriageLevel;
import com.vidacruz.clinical.domain.model.urgency.UrgencyState;
import com.vidacruz.clinical.domain.services.GetUrgencyStateUseCase;
import com.vidacruz.clinical.infrastructure.adapters.UrgencyStateUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;

/**
 * RF-04B: current state of the emergency room (API-05).
 */
@RestController
public class UrgencyStateController {

    private final GetUrgencyStateUseCase getUrgencyState;

    public UrgencyStateController(GetUrgencyStateUseCase getUrgencyState) {
        this.getUrgencyState = getUrgencyState;
    }

    @GetMapping("/asistente/estado-urgencias")
    public Mono<ResponseEntity<UrgencyStateResponse>> current() {
        return getUrgencyState.current()
                .map(state -> ResponseEntity.ok(toResponse(state)))
                .onErrorResume(UrgencyStateUnavailableException.class, error ->
                        Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()));
    }

    static UrgencyStateResponse toResponse(UrgencyState state) {
        var byLevel = new LinkedHashMap<String, Long>();
        for (var level : TriageLevel.values()) {
            byLevel.put(level.code(), state.activeByLevel().get(level));
        }
        return new UrgencyStateResponse(state.totalActive(), byLevel, state.updatedAt().orElse(null));
    }
}
