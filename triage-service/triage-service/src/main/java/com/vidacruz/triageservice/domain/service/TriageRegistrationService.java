package com.vidacruz.triageservice.domain.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.application.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.application.dto.PatientRegistrationResponse;
import com.vidacruz.triageservice.domain.model.PatientRegistration;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.event.domain.OutboxEvento;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventoRepository;
import com.vidacruz.triageservice.infrastructure.config.SlaProperties;
import com.vidacruz.triageservice.infrastructure.persistence.PatientEntity;
import com.vidacruz.triageservice.infrastructure.persistence.PatientRepository;

import reactor.core.publisher.Mono;

/** RF-01 use case: validate, classify, persist and record the registration event in the outbox. */
@Service
public class TriageRegistrationService {

	static final String REGISTERED_STATUS = "REGISTRADO";
	static final String REGISTERED_EVENT = "paciente_registrado";

	private final PatientRepository patientRepository;
	private final OutboxEventoRepository outboxRepository;
	private final TriageClassifier classifier;
	private final SlaProperties slaProperties;
	private final ObjectMapper objectMapper;

	public TriageRegistrationService(PatientRepository patientRepository,
			OutboxEventoRepository outboxRepository,
			TriageClassifier classifier,
			SlaProperties slaProperties,
			ObjectMapper objectMapper) {
		this.patientRepository = patientRepository;
		this.outboxRepository = outboxRepository;
		this.classifier = classifier;
		this.slaProperties = slaProperties;
		this.objectMapper = objectMapper;
	}

	public Mono<PatientRegistrationResponse> register(PatientRegistrationRequest request) {
		PatientRegistration patient = request.toDomain();
		TriageLevel level = classifier.classify(patient);
		int slaMinutes = slaProperties.minutesFor(level);
		UUID patientId = UUID.randomUUID();
		Instant registeredAt = Instant.now();
		UUID correlationId = UUID.randomUUID();

		PatientEntity entity = new PatientEntity(
				patientId,
				patient.fullName(),
				patient.age(),
				patient.heartRate(),
				patient.systolic(),
				patient.diastolic(),
				patient.spo2(),
				BigDecimal.valueOf(patient.temperature()),
				patient.pain(),
				patient.gcs(),
				patient.complaint(),
				level.name(),
				slaMinutes,
				REGISTERED_STATUS,
				false,
				registeredAt);

		OutboxEvento event = new OutboxEvento(
				UUID.randomUUID(),
				REGISTERED_EVENT,
				eventPayload(patientId, level, slaMinutes, registeredAt),
				patientId,
				1,
				OffsetDateTime.now(),
				correlationId);

		return patientRepository.save(entity)
				.then(outboxRepository.save(event))
				.thenReturn(new PatientRegistrationResponse(
						patientId, level.name(), slaMinutes, REGISTERED_STATUS, registeredAt));
	}

	private String eventPayload(UUID patientId, TriageLevel level, int slaMinutes, Instant registeredAt) {
		try {
			return objectMapper.writeValueAsString(Map.of(
					"id", patientId.toString(),
					"nivel", level.name(),
					"slaMinutos", slaMinutes,
					"estado", REGISTERED_STATUS,
					"fechaRegistro", registeredAt.toString(),
					"eventType", REGISTERED_EVENT));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("No se pudo serializar el evento de registro", exception);
		}
	}
}
