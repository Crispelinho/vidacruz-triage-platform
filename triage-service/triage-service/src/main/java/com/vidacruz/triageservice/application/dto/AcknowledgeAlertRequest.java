package com.vidacruz.triageservice.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Body of {@code POST /triage/alertas/{id}/reconocimiento}. */
public record AcknowledgeAlertRequest(@JsonProperty("responsable") String responsible) {
}
