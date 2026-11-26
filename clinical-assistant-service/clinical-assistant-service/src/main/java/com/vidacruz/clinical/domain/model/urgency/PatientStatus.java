package com.vidacruz.clinical.domain.model.urgency;

/**
 * Clinical status of a patient, as published by the triage service.
 */
public enum PatientStatus {

    REGISTRADO,
    EN_CONSULTA,
    ALTA;

    /** RF-04B: every patient not discharged is still in the emergency room. */
    public boolean isActive() {
        return this != ALTA;
    }
}
