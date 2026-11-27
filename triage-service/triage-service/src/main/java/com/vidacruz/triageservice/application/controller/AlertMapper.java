package com.vidacruz.triageservice.application.controller;

import com.vidacruz.triageservice.application.dto.AlertResponse;
import com.vidacruz.triageservice.domain.model.AlertStatus;
import com.vidacruz.triageservice.domain.model.MedicalAlert;

/** Translates RF-06 alerts to the Spanish HTTP contract. */
final class AlertMapper {

	static AlertResponse toResponse(MedicalAlert alert) {
		return new AlertResponse(
				alert.id(), alert.patientId(), alert.level().name(), alert.slaMinutes(),
				alert.registeredAt(), alert.escalatedAt(), alert.waitMinutes(),
				status(alert.status()), alert.responsible(), alert.acknowledgedAt(),
				alert.resolvedAt(), alert.updatedAt());
	}

	private static String status(AlertStatus status) {
		return switch (status) {
			case PENDIENTE -> "PENDIENTE";
			case RECONOCIDA -> "RECONOCIDA";
			case RESUELTA -> "RESUELTA";
		};
	}

	private AlertMapper() {
	}
}
