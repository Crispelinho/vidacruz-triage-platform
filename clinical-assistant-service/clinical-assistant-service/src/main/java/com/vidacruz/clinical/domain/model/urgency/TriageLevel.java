package com.vidacruz.clinical.domain.model.urgency;

/**
 * Triage levels of RN-01, from the most to the least severe.
 */
public enum TriageLevel {

    CRITICAL,
    URGENT,
    URGENT_2,
    NON_URGENT,
    NON_URGENT_2;

    /** Code published by the triage service in its events and used by its API. */
    public String code() {
        return switch (this) {
            case CRITICAL -> "CRITICO";
            case URGENT -> "URGENTE";
            case URGENT_2 -> "URGENTE_2";
            case NON_URGENT -> "NO_URGENTE";
            case NON_URGENT_2 -> "NO_URGENTE_2";
        };
    }

    /** Parses the code published by the triage service. */
    public static TriageLevel fromCode(String code) {
        for (var level : values()) {
            if (level.code().equals(code)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown triage level " + code);
    }
}
