package com.vidacruz.triageservice.domain.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.application.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.application.dto.PatientRegistrationResponse;
import com.vidacruz.triageservice.domain.exception.IdempotencyKeyReusedException;
import com.vidacruz.triageservice.domain.model.PatientRegistration;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.model.TriageMetrics;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventWriter;
import com.vidacruz.triageservice.infrastructure.config.SlaProperties;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryEntity;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryRepository;
import com.vidacruz.triageservice.infrastructure.persistence.IdempotencyKeyEntity;
import com.vidacruz.triageservice.infrastructure.persistence.IdempotencyKeyRepository;
import com.vidacruz.triageservice.infrastructure.persistence.PatientEntity;
import com.vidacruz.triageservice.infrastructure.persistence.PatientRepository;

import reactor.core.publisher.Mono;

/** RF-01 use case: validate, classify, persist, audit and record the registration event atomically. */
@Service
public class TriageRegistrationService {

	public static final String REGISTERED_STATUS = "REGISTRADO";
	public static final String REGISTERED_EVENT = "paciente_registrado";
	public static final String REGISTRATION_AUDIT = "REGISTRO";
	private static final int EXPIRY_HOURS = 24;

	private final PatientRepository patientRepository;
	private final AuditEntryRepository auditRepository;
	private final OutboxEventWriter eventWriter;
	private final IdempotencyKeyRepository idempotencyRepository;
	private final ObjectMapper objectMapper;
	private final TriageClassifier classifier;
	private final SlaProperties slaProperties;
	private final TransactionalOperator transactionalOperator;
	private final Clock clock;
	private final TriageMetrics metrics;

	public TriageRegistrationService(PatientRepository patientRepository,
			AuditEntryRepository auditRepository,
			OutboxEventWriter eventWriter,
			IdempotencyKeyRepository idempotencyRepository,
			ObjectMapper objectMapper,
			TriageClassifier classifier,
			SlaProperties slaProperties,
			TransactionalOperator transactionalOperator,
			TriageMetrics metrics) {
		this(patientRepository, auditRepository, eventWriter, idempotencyRepository, objectMapper, classifier,
				slaProperties, transactionalOperator, Clock.systemUTC(), metrics);
	}

	@Autowired
	public TriageRegistrationService(PatientRepository patientRepository,
			AuditEntryRepository auditRepository,
			OutboxEventWriter eventWriter,
			IdempotencyKeyRepository idempotencyRepository,
			ObjectMapper objectMapper,
			TriageClassifier classifier,
			SlaProperties slaProperties,
			TransactionalOperator transactionalOperator,
			Clock clock,
			TriageMetrics metrics) {
		this.patientRepository = patientRepository;
		this.auditRepository = auditRepository;
		this.eventWriter = eventWriter;
		this.idempotencyRepository = idempotencyRepository;
		this.objectMapper = objectMapper;
		this.classifier = classifier;
		this.slaProperties = slaProperties;
		this.transactionalOperator = transactionalOperator;
		this.clock = clock;
		this.metrics = metrics;
	}

	public Mono<PatientRegistrationResponse> register(PatientRegistrationRequest request) {
		Registration registration = prepare(request);
		return transactionalOperator.transactional(registration.write())
				.doOnNext(written -> metrics.patientClassified(registration.level()))
				.thenReturn(registration.response());
	}

	/** DEC-15 / TST-IDM: replay the original response for the same key and body, conflict on body reuse. */
	public Mono<PatientRegistrationResponse> register(PatientRegistrationRequest request, String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return register(request);
		}
		// Validation runs before hashing and storing, so a 400 never reserves the key.
		String requestHash = RequestFingerprint.of(request.toDomain());
		String key = idempotencyKey.trim();

		return transactionalOperator.transactional(
				idempotencyRepository.findById(key)
						.flatMap(stored -> replay(stored, requestHash))
						.switchIfEmpty(Mono.defer(() -> createWithKey(request, key, requestHash))));
	}

	private Mono<PatientRegistrationResponse> replay(IdempotencyKeyEntity stored, String requestHash) {
		if (!requestHash.equals(stored.requestHash())) {
			return Mono.error(new IdempotencyKeyReusedException(stored.key()));
		}
		try {
			return Mono.just(objectMapper.readValue(stored.responseBody(), PatientRegistrationResponse.class));
		} catch (JsonProcessingException exception) {
			return Mono.error(new IllegalStateException("Stored idempotency response is not readable", exception));
		}
	}

	private Mono<PatientRegistrationResponse> createWithKey(PatientRegistrationRequest request, String key,
			String requestHash) {
		Instant now = clock.instant();
		return idempotencyRepository.insertPending(key, requestHash, now,
				now.plus(EXPIRY_HOURS, ChronoUnit.HOURS))
				.flatMap(inserted -> {
					if (inserted != 1) {
						// The losing request waits for the winner's transaction, then replays its response.
						return idempotencyRepository.findByIdForUpdate(key)
								.flatMap(existing -> replay(existing, requestHash))
								.switchIfEmpty(Mono.defer(() -> createWithKey(request, key, requestHash)));
					}
					Registration registration = prepare(request, now);
					String responseBody;
					try {
						responseBody = objectMapper.writeValueAsString(registration.response());
					} catch (JsonProcessingException exception) {
						return Mono.error(new IllegalStateException("Cannot store idempotency response", exception));
					}
					return registration.write()
							.then(idempotencyRepository.complete(key, registration.response().id(), 201,
									responseBody))
							.doOnNext(completed -> metrics.patientClassified(registration.level()))
							.thenReturn(registration.response());
				});
	}

	private Registration prepare(PatientRegistrationRequest request) {
		return prepare(request, clock.instant());
	}

	private Registration prepare(PatientRegistrationRequest request, Instant registeredAt) {
		PatientRegistration patient = request.toDomain();
		TriageLevel level = classifier.classify(patient);
		int slaMinutes = slaProperties.minutesFor(level);
		UUID patientId = UUID.randomUUID();

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
				registeredAt,
				registeredAt);

		Mono<?> write = patientRepository.save(entity)
				.then(auditRepository.save(new AuditEntryEntity(
						UUID.randomUUID(), patientId, registeredAt, null,
						REGISTERED_STATUS, REGISTRATION_AUDIT)))
				.then(eventWriter.write(patientId, REGISTERED_EVENT, Map.of(
						"nivel", level.name(),
						"slaMinutos", slaMinutes,
						"estado", REGISTERED_STATUS,
						"fechaRegistro", registeredAt.toString()), registeredAt));

		return new Registration(new PatientRegistrationResponse(
				patientId, level.name(), slaMinutes, REGISTERED_STATUS, registeredAt), write, level);
	}

	private record Registration(PatientRegistrationResponse response, Mono<?> write, TriageLevel level) {
	}
}
