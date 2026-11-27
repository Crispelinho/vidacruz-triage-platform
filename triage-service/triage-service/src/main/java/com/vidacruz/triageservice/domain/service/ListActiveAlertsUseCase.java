package com.vidacruz.triageservice.domain.service;

import org.springframework.stereotype.Service;

import com.vidacruz.triageservice.domain.model.MedicalAlert;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;

import reactor.core.publisher.Flux;

/** RF-06: active alerts, most severe first and then oldest first. */
@Service
public class ListActiveAlertsUseCase {

	private final MedicalAlertRepository alertRepository;

	public ListActiveAlertsUseCase(MedicalAlertRepository alertRepository) {
		this.alertRepository = alertRepository;
	}

	public Flux<MedicalAlert> list() {
		return alertRepository.findActive();
	}
}
