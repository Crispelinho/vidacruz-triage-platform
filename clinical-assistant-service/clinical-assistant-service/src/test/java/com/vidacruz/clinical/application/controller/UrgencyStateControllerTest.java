package com.vidacruz.clinical.application.controller;

import com.vidacruz.clinical.domain.model.urgency.TriageLevel;
import com.vidacruz.clinical.domain.model.urgency.UrgencyState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UrgencyStateControllerTest {

    @Test
    void exposesAllLevelsWithTheTriageCodes() {
        var state = new UrgencyState(Map.of(TriageLevel.CRITICAL, 2L),
                Optional.of(Instant.parse("2026-11-26T10:00:00Z")));

        var response = UrgencyStateController.toResponse(state);

        assertThat(response.totalActive()).isEqualTo(2);
        assertThat(response.activeByLevel()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "CRITICO", 2L,
                "URGENTE", 0L,
                "URGENTE_2", 0L,
                "NO_URGENTE", 0L,
                "NO_URGENTE_2", 0L));
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-11-26T10:00:00Z"));
    }
}
