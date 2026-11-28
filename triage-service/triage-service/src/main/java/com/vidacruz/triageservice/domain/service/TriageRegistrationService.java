package com.vidacruz.triageservice.domain.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import com.vidacruz.triageservice.domain.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.domain.dto.PatientRegistrationResponse;
import com.vidacruz.triageservice.domain.exception.IdempotencyKeyReusedException;
import com.vidacruz.triageservice.domain.model.AuditEntry;
import com.vidacruz.triageservice.domain.model.IdempotencyKey;
import com.vidacruz.triageservice.domain.model.PatientRegistration;
import com.vidacruz.triageservice.domain.model.PatientSnapshot;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.model.TriageMetrics;
import com.vidacruz.triageservice.domain.port.AuditStore;
import com.vidacruz.triageservice.domain.port.IdempotencyStore;
import com.vidacruz.triageservice.domain.port.OutboxWriter;
import com.vidacruz.triageservice.domain.port.PatientStore;
import com.vidacruz.triageservice.domain.port.RegistrationResponseCodec;
import com.vidacruz.triageservice.domain.port.SlaPolicy;
import com.vidacruz.triageservice.domain.port.TransactionalExecutor;

import reactor.core.publisher.Mono;

/** RF-01 use case: validate, classify, persist, audit and record the registration event atomically. */
public class TriageRegistrationService {

	private static final Logger log = Logger.getLogger(TriageRegistrationService.class.getName());

	public static final String REGISTERED_STATUS = "REGISTRADO";
	public static final String REGISTERED_EVENT = "paciente_registrado";
	public static final String REGISTRATION_AUDIT = "REGISTRO";
	private static final int EXPIRY_HOURS = 24;

	private final PatientStore patientStore;
	private final AuditStore auditStore;
	private final OutboxWriter outboxWriter;
	private final IdempotencyStore idempotencyStore;
	private final RegistrationResponseCodec responseCodec;
	private final TriageClassifier classifier;
	private final SlaPolicy slaPolicy;
	private final TransactionalExecutor transactions;
	private final Clock clock;
	private final TriageMetrics metrics;

	public TriageRegistrationService(PatientStore patientStore,
			AuditStore auditStore,
			OutboxWriter outboxWriter,
			IdempotencyStore idempotencyStore,
			RegistrationResponseCodec responseCodec,
			TriageClassifier classifier,
			SlaPolicy slaPolicy,
			TransactionalExecutor transactions,
			Clock clock,
			TriageMetrics metrics) {
		this.patientStore = patientStore;
		this.auditStore = auditStore;
		this.outboxWriter = outboxWriter;
		this.idempotencyStore = idempotencyStore;
		this.responseCodec = responseCodec;
		this.classifier = classifier;
		this.slaPolicy = slaPolicy;
		this.transactions = transactions;
		this.clock = clock;
		this.metrics = metrics;
	}

	public Mono<PatientRegistrationResponse> register(PatientRegistrationRequest request) {
		Registration registration = prepare(request);
		return transactions.transactional(registration.write())
				.doOnSuccess(written -> metrics.patientClassified(registration.level()))
				.thenReturn(registration.response())
				.doOnNext(TriageRegistrationService::logRegistered);
	}

	/** DEC-15 / TST-IDM: replay the original response for the same key and body, conflict on body reuse. */
	public Mono<PatientRegistrationResponse> register(PatientRegistrationRequest request, String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return register(request);
		}
		// Validation runs before hashing and storing, so a 400 never reserves the key.
		String requestHash = RequestFingerprint.of(request.toDomain());
		String key = idempotencyKey.trim();

		return transactions.transactional(
				idempotencyStore.findById(key)
						.flatMap(stored -> replay(stored, requestHash))
						.switchIfEmpty(Mono.defer(() -> createWithKey(request, key, requestHash))));
	}

	private Mono<PatientRegistrationResponse> replay(IdempotencyKey stored, String requestHash) {
		if (!requestHash.equals(stored.requestHash())) {
			return Mono.error(new IdempotencyKeyReusedException(stored.key()));
		}
		return Mono.just(responseCodec.decode(stored.responseBody()));
	}

	private Mono<PatientRegistrationResponse> createWithKey(PatientRegistrationRequest request, String key,
			String requestHash) {
		Instant now = clock.instant();
		return idempotencyStore.insertPending(key, requestHash, now,
				now.plus(EXPIRY_HOURS, ChronoUnit.HOURS))
				.flatMap(inserted -> {
					if (inserted != 1) {
						// The losing request waits for the winner's transaction, then replays its response.
						return idempotencyStore.findByIdForUpdate(key)
								.flatMap(existing -> replay(existing, requestHash))
								.switchIfEmpty(Mono.defer(() -> createWithKey(request, key, requestHash)));
					}
					Registration registration = prepare(request, now);
					String responseBody = responseCodec.encode(registration.response());
					return registration.write()
							.then(idempotencyStore.complete(key, registration.response().id(), 201,
									responseBody))
							.doOnSuccess(completed -> metrics.patientClassified(registration.level()))
							.thenReturn(registration.response())
							.doOnNext(TriageRegistrationService::logRegistered);
				});
	}

	/** Logs identifiers and the classification only; name and complaint stay out of the logs. */
	private static void logRegistered(PatientRegistrationResponse response) {
		log.info(() -> "Patient " + response.id() + " registered as " + response.level()
				+ " with a " + response.slaMinutes() + "-minute SLA");
	}

	private Registration prepare(PatientRegistrationRequest request) {
		return prepare(request, clock.instant());
	}

	private Registration prepare(PatientRegistrationRequest request, Instant registeredAt) {
		PatientRegistration patient = request.toDomain();
		TriageLevel level = classifier.classify(patient);
		int slaMinutes = slaPolicy.minutesFor(level);
		UUID patientId = UUID.randomUUID();

		PatientSnapshot entity = new PatientSnapshot(
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

		Mono<Void> write = patientStore.save(entity)
				.then(auditStore.append(new AuditEntry(
						UUID.randomUUID(), patientId, registeredAt, null,
						REGISTERED_STATUS, REGISTRATION_AUDIT)))
				.then(outboxWriter.write(patientId, REGISTERED_EVENT, Map.of(
						"nivel", level.name(),
						"slaMinutos", slaMinutes,
						"estado", REGISTERED_STATUS,
						"fechaRegistro", registeredAt.toString()), registeredAt));

		return new Registration(new PatientRegistrationResponse(
				patientId, level.name(), slaMinutes, REGISTERED_STATUS, registeredAt), write, level);
	}

	private record Registration(PatientRegistrationResponse response, Mono<Void> write, TriageLevel level) {
	}
}
