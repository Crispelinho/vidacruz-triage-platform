package com.vidacruz.clinical.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the events of the triage are read from.
 *
 * @param bootstrapServers Kafka brokers
 * @param topic            topic of the triage events (triaje.eventos)
 * @param groupId          consumer group of the projection
 */
@ConfigurationProperties(prefix = "adapters.kafka.patient-events")
public record TriageEventsProperties(String bootstrapServers, String topic, String groupId) {

    public TriageEventsProperties {
        if (isBlank(bootstrapServers) || isBlank(topic) || isBlank(groupId)) {
            throw new IllegalArgumentException(
                    "adapters.kafka.patient-events needs bootstrap-servers, topic and group-id");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
