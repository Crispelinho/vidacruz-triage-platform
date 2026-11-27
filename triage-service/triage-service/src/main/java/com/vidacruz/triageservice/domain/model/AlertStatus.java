package com.vidacruz.triageservice.domain.model;

/** RF-06 alert lifecycle: a doctor takes a pending alert and the patient flow resolves it automatically. */
public enum AlertStatus {
	PENDIENTE,
	RECONOCIDA,
	RESUELTA
}
