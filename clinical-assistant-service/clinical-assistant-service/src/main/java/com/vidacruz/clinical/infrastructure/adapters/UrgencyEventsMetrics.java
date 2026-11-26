package com.vidacruz.clinical.infrastructure.adapters;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;

/**
 * assistant.urgency.events{result=applied|ignored_older|skipped}, with every series registered at startup.
 */
final class UrgencyEventsMetrics {

    static final String EVENTS = "assistant.urgency.events";
    static final String RESULT_TAG = "result";
    static final String APPLIED = "applied";
    static final String IGNORED_OLDER = "ignored_older";
    static final String SKIPPED = "skipped";

    private final Map<Boolean, Counter> projected;
    private final Counter skipped;

    UrgencyEventsMetrics(MeterRegistry registry) {
        projected = Map.of(
                Boolean.TRUE, counter(registry, APPLIED),
                Boolean.FALSE, counter(registry, IGNORED_OLDER));
        skipped = counter(registry, SKIPPED);
    }

    void projected(Boolean applied) {
        projected.get(applied).increment();
    }

    void skipped() {
        skipped.increment();
    }

    private static Counter counter(MeterRegistry registry, String result) {
        return Counter.builder(EVENTS)
                .description("Triage events read by the urgency projection, by what was done with them")
                .tag(RESULT_TAG, result)
                .register(registry);
    }
}
