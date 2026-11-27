package com.vidacruz.triageservice.domain.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.vidacruz.triageservice.application.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.application.dto.PatientRegistrationResponse;
import com.vidacruz.triageservice.domain.model.PatientRegistration;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventWriter;
import com.vidacruz.triageservice.infrastructure.config.SlaProperties;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryEntity;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryRepository;
import com.vidacruz.triageservice.infrastructure.persistence.PatientEntity;
import com.vidacruz.triageservice.infrastructure.persistence.PatientRepository;

import reactor.core.publisher.Mono;

/** RF-01 use case: validate, classify, persist, audit and record the registration event atomically. */
@Service
public class TriageRegistrationService {

	public static final String REGISTERED_STATUS = "REGISTRADO";
	public static final String REGISTERED_EVENT = "paciente_registrado";
	public static final String REGISTRATION_AUDIT = "REGISTRO";

	private final PatientRepository patientRepository;
	private final AuditEntryRepository auditRepository;
	private final OutboxEventWriter eventWriter;
	private final TriageClassifier classifier;
	private final SlaProperties slaProperties;
	private final TransactionalOperator transactionalOperator;
	private final Clock clock;

	public TriageRegistrationService(PatientRepository patientRepository,
			AuditEntryRepository auditRepository,
			OutboxEventWriter eventWriter,
			TriageClassifier classifier,
			SlaProperties slaProperties,
			TransactionalOperator transactionalOperator,
			Clock clock) {
		this.patientRepository = patientRepository;
		this.auditRepository = auditRepository;
		this.eventWriter = eventWriter;
		this.classifier = classifier;
		this.slaProperties = slaProperties;
		this.transactionalOperator = transactionalOperator;
		this.clock = clock;
	}

	public Mono<PatientRegistrationResponse> register(PatientRegistrationRequest request) {
		PatientRegistration patient = request.toDomain();
		TriageLevel level = classifier.classify(patient);
		int slaMinutes = slaProperties.minutesFor(level);
		UUID patientId = UUID.randomUUID();
		Instant registeredAt = clock.instant();

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
				registeredAt,
				registeredAt);

		Mono<?> atomicWrite = patientRepository.save(entity)
				.then(auditRepository.save(new AuditEntryEntity(
						UUID.randomUUID(), patientId, registeredAt, null,
						REGISTERED_STATUS, REGISTRATION_AUDIT)))
				.then(eventWriter.write(patientId, REGISTERED_EVENT, Map.of(
						"nivel", level.name(),
						"slaMinutos", slaMinutes,
						"estado", REGISTERED_STATUS,
						"fechaRegistro", registeredAt.toString()), registeredAt));

		return transactionalOperator.transactional(atomicWrite)
				.thenReturn(new PatientRegistrationResponse(
						patientId, level.name(), slaMinutes, REGISTERED_STATUS, registeredAt));
	}
}
