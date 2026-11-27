package com.vidacruz.triageservice.domain.exception;

import java.util.UUID;

/** TRG-009: the alert was already acknowledged or resolved, so it cannot be acknowledged again. */
public class AlertConflictException extends RuntimeException {

	public AlertConflictException(UUID alertId) {
		super("La alerta " + alertId + " ya fue reconocida o resuelta");
	}
}
