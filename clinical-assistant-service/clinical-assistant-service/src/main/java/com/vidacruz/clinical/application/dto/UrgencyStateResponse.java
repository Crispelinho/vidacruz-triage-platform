package com.vidacruz.clinical.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Active patients of the emergency room per triage level, built from the events of the triage.
 */
public record UrgencyStateResponse(
        @JsonProperty("totalActivos") long totalActive,
        @JsonProperty("porNivel") Map<String, Long> activeByLevel,
        @JsonProperty("actualizadoEn") Instant updatedAt) {
}
