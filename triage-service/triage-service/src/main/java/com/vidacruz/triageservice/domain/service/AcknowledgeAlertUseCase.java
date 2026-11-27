package com.vidacruz.triageservice.domain.service;

import java.time.Instant;
import java.util.UUID;

import com.vidacruz.triageservice.domain.exception.AlertConflictException;
import com.vidacruz.triageservice.domain.exception.AlertNotFoundException;
import com.vidacruz.triageservice.domain.exception.InvalidAlertRequestException;
import com.vidacruz.triageservice.domain.model.MedicalAlert;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.TriageMetrics;

import reactor.core.publisher.Mono;

/** RF-06: a team member takes a pending alert. The conditional update prevents two doctors taking the same alert. */
public class AcknowledgeAlertUseCase {

	private static final int MIN_LENGTH = 3;
	private static final int MAX_LENGTH = 100;

	private final MedicalAlertRepository alertRepository;
	private final TriageMetrics metrics;

	public AcknowledgeAlertUseCase(MedicalAlertRepository alertRepository, TriageMetrics metrics) {
		this.alertRepository = alertRepository;
		this.metrics = metrics;
	}

	public Mono<MedicalAlert> acknowledge(UUID id, String responsible, Instant at) {
		var name = normalize(responsible);
		return alertRepository.acknowledge(id, name, at)
				.flatMap(updated -> updated
						? alertRepository.findById(id)
						: explainFailure(id))
				.doOnNext(alert -> metrics.alertAcknowledged(alert.level()));
	}

	private Mono<MedicalAlert> explainFailure(UUID id) {
		return alertRepository.findById(id)
				.flatMap(alert -> Mono.<MedicalAlert>error(new AlertConflictException(switch (alert.status()) {
					case RESUELTA -> "La alerta ya fue resuelta: el paciente fue atendido o dado de alta";
					case RECONOCIDA -> "La alerta ya fue reconocida por " + alert.responsible();
					case PENDIENTE -> "La alerta " + id + " ya no se puede reconocer";
				})))
				.switchIfEmpty(Mono.error(new AlertNotFoundException(id)));
	}

	private static String normalize(String responsible) {
		var name = responsible == null ? "" : responsible.strip();
		if (name.length() < MIN_LENGTH || name.length() > MAX_LENGTH) {
			throw new InvalidAlertRequestException("responsable",
					"El responsable debe tener entre " + MIN_LENGTH + " y " + MAX_LENGTH + " caracteres");
		}
		return name;
	}
}
