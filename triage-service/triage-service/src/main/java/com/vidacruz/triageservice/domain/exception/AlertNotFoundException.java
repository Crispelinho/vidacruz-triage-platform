package com.vidacruz.triageservice.domain.exception;

import java.util.UUID;

/** TRG-008: the requested alert does not exist. */
public class AlertNotFoundException extends RuntimeException {

	public AlertNotFoundException(UUID alertId) {
		super("No existe la alerta " + alertId);
	}
}
