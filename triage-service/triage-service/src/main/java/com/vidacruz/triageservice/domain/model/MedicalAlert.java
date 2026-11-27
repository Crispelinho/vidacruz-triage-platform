package com.vidacruz.triageservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * RF-06 escalation alert. It deliberately carries no patient name or complaint: it is meant for shared screens and
 * the full clinical detail is fetched separately with the same API key.
 *
 * @param id           alert id
 * @param patientId    escalated patient
 * @param level        triage level at escalation time
 * @param slaMinutes   SLA that was exceeded
 * @param registeredAt patient registration
 * @param escalatedAt  escalation time
 * @param waitMinutes  minutes from registration to escalation
 * @param status       current lifecycle status
 * @param responsible  team member who acknowledged the alert, if any
 * @param acknowledgedAt when it was taken, if any
 * @param resolvedAt   when the patient left the waiting list, if any
 * @param updatedAt    last change, used by SSE clients to deduplicate
 */
public record MedicalAlert(UUID id, UUID patientId, TriageLevel level, int slaMinutes,
                           Instant registeredAt, Instant escalatedAt, BigDecimal waitMinutes,
                           AlertStatus status, String responsible, Instant acknowledgedAt,
                           Instant resolvedAt, Instant updatedAt) {

	public boolean isPending() {
		return status == AlertStatus.PENDIENTE;
	}

	public boolean isActive() {
		return status == AlertStatus.PENDIENTE || status == AlertStatus.RECONOCIDA;
	}
}
