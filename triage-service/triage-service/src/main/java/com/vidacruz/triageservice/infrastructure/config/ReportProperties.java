package com.vidacruz.triageservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings of the operation report (RF-05).
 *
 * @param maxRange longest range a report may cover, 90 natural dates by default (OD-4)
 */
@ConfigurationProperties(prefix = "app.report")
public record ReportProperties(Duration maxRange) {
}
