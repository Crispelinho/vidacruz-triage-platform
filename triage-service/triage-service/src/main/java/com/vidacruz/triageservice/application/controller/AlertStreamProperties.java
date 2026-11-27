package com.vidacruz.triageservice.application.controller;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** SSE idle comment interval. It must stay below the ingress read timeout. */
@ConfigurationProperties(prefix = "alerts.stream")
public record AlertStreamProperties(Duration heartbeat) {

	public AlertStreamProperties {
		if (heartbeat == null || heartbeat.isZero() || heartbeat.isNegative()) {
			throw new IllegalArgumentException("alerts.stream.heartbeat must be a positive duration");
		}
	}
}
