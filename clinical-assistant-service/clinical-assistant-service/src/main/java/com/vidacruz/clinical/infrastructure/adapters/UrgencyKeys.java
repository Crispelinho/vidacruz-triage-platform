package com.vidacruz.clinical.infrastructure.adapters;

import com.vidacruz.clinical.domain.model.urgency.TriageLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Keys of the projection. They share the hash tag {@code {urgency}} so the scripts that touch several keys also work
 * on a Redis cluster.
 */
final class UrgencyKeys {

    private static final String PREFIX = "{urgency}:";
    static final String UPDATED_AT = PREFIX + "updated-at";
    static final String INITIALIZED = PREFIX + "initialized";

    private UrgencyKeys() {
    }

    static String patient(UUID patientId) {
        return PREFIX + "patient:" + patientId;
    }

    static String active(TriageLevel level) {
        return PREFIX + "active:" + level.code();
    }

    /** Active sets of every level, in enum order, matching the Lua script. */
    static List<String> activeOfEveryLevel() {
        var keys = new ArrayList<String>();
        for (var level : TriageLevel.values()) {
            keys.add(active(level));
        }
        return keys;
    }
}
