package com.vidacruz.clinical.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param dischargedRetention how long a discharged patient is remembered, so a late older event cannot bring it back
 */
@ConfigurationProperties(prefix = "adapters.urgency-state")
public record UrgencyStateProperties(Duration dischargedRetention) {

    public UrgencyStateProperties {
        if (dischargedRetention == null || dischargedRetention.toSeconds() <= 0) {
            throw new IllegalArgumentException("adapters.urgency-state.discharged-retention must be at least 1s");
        }
    }
}
