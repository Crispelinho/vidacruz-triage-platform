package com.vidacruz.triageservice.domain.service;

import org.springframework.stereotype.Service;

import com.vidacruz.triageservice.domain.model.MedicalAlert;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;

import reactor.core.publisher.Flux;

/**
 * RF-06 change feed: the active snapshot plus all later changes. Changes are subscribed first so an alert that
 * changes while the snapshot is read cannot be missed. Duplicate delivery is possible; clients deduplicate by
 * {@code actualizadaEn}.
 */
@Service
public class WatchAlertsUseCase {

	private final MedicalAlertRepository alertRepository;

	public WatchAlertsUseCase(MedicalAlertRepository alertRepository) {
		this.alertRepository = alertRepository;
	}

	public Flux<MedicalAlert> watch() {
		return Flux.merge(alertRepository.changes(), alertRepository.findActive());
	}
}
