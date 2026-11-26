package com.vidacruz.clinical.infrastructure.adapters;

import com.vidacruz.clinical.domain.model.urgency.TriageLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RedisUrgencyStateRepositoryTest {

    @Test
    void mapsRedisResultToEveryLevelAndTimestamp() {
        var micros = ChronoUnit.MICROS.between(Instant.EPOCH, Instant.parse("2026-11-26T10:00:00Z"));
        var state = RedisUrgencyStateRepository.toState(
                List.of(1L, 2L, 0L, 3L, 1L, String.valueOf(micros)));

        assertThat(state.activeByLevel()).containsEntry(TriageLevel.CRITICAL, 1L)
                .containsEntry(TriageLevel.URGENT, 2L)
                .containsEntry(TriageLevel.URGENT_2, 0L)
                .containsEntry(TriageLevel.NON_URGENT, 3L)
                .containsEntry(TriageLevel.NON_URGENT_2, 1L);
        assertThat(state.totalActive()).isEqualTo(7);
        assertThat(state.updatedAt()).isEqualTo(Optional.of(Instant.parse("2026-11-26T10:00:00Z")));
    }

    @Test
    void mapsEmptyProjectionWithoutTimestamp() {
        var state = RedisUrgencyStateRepository.toState(List.of(0L, 0L, 0L, 0L, 0L, ""));

        assertThat(state.totalActive()).isZero();
        assertThat(state.updatedAt()).isEmpty();
    }
}
