package com.vidacruz.triageservice.domain.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vidacruz.triageservice.domain.exception.IdempotencyKeyReusedException;
import com.vidacruz.triageservice.domain.model.AuditEntry;
import com.vidacruz.triageservice.domain.model.AuditLog;
import com.vidacruz.triageservice.domain.model.EventOutbox;
import com.vidacruz.triageservice.domain.model.IdempotencyRecord;
import com.vidacruz.triageservice.domain.model.IdempotencyStore;
import com.vidacruz.triageservice.domain.model.Patient;
import com.vidacruz.triageservice.domain.model.PatientRegistration;
import com.vidacruz.triageservice.domain.model.PatientStore;
import com.vidacruz.triageservice.domain.model.RegistrationResult;
import com.vidacruz.triageservice.domain.model.RequestFingerprinter;
import com.vidacruz.triageservice.domain.model.SlaPolicy;
import com.vidacruz.triageservice.domain.model.TransactionRunner;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.model.TriageMetrics;

import reactor.core.publisher.Mono;

/** RF-01 use case: classify, persist, audit and record the registration event atomically. */
public class TriageRegistrationService {

	private static final Logger log = LoggerFactory.getLogger(TriageRegistrationService.class);

	public static final String REGISTERED_STATUS = "REGISTRADO";
	public static final String REGISTERED_EVENT = "paciente_registrado";
	public static final String REGISTRATION_AUDIT = "REGISTRO";
	private static final int EXPIRY_HOURS = 24;

	private final PatientStore patients;
	private final AuditLog auditLog;
	private final EventOutbox outbox;
	private final IdempotencyStore idempotencyStore;
	private final RequestFingerprinter fingerprinter;
	private final TriageClassifier classifier;
	private final SlaPolicy slaPolicy;
	private final TransactionRunner transactions;
	private final Clock clock;
	private final TriageMetrics metrics;

	public TriageRegistrationService(PatientStore patients,
			AuditLog auditLog,
			EventOutbox outbox,
			IdempotencyStore idempotencyStore,
			RequestFingerprinter fingerprinter,
			TriageClassifier classifier,
			SlaPolicy slaPolicy,
			TransactionRunner transactions,
			Clock clock,
			TriageMetrics metrics) {
		this.patients = patients;
		this.auditLog = auditLog;
		this.outbox = outbox;
		this.idempotencyStore = idempotencyStore;
		this.fingerprinter = fingerprinter;
		this.classifier = classifier;
		this.slaPolicy = slaPolicy;
		this.transactions = transactions;
		this.clock = clock;
		this.metrics = metrics;
	}

	public Mono<RegistrationResult> register(PatientRegistration patient) {
		Registration registration = prepare(patient, clock.instant());
		return transactions.inTransaction(registration.write().thenReturn(registration.result()))
				.doOnNext(written -> metrics.patientClassified(registration.result().level()))
				.doOnNext(TriageRegistrationService::logRegistered);
	}

	/** DEC-15 / TST-IDM: replay the original response for the same key and body, conflict on body reuse. */
	public Mono<RegistrationResult> register(PatientRegistration patient, String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return register(patient);
		}
		// The registration is already validated, so a 400 never reserves the key.
		String requestHash = fingerprinter.fingerprint(patient);
		String key = idempotencyKey.trim();

		return transactions.inTransaction(
				idempotencyStore.find(key)
						.flatMap(stored -> replay(stored, requestHash))
						.switchIfEmpty(Mono.defer(() -> createWithKey(patient, key, requestHash))));
	}

	private static Mono<RegistrationResult> replay(IdempotencyRecord stored, String requestHash) {
		if (!requestHash.equals(stored.requestHash())) {
			return Mono.error(new IdempotencyKeyReusedException(stored.key()));
		}
		return Mono.just(stored.response());
	}

	private Mono<RegistrationResult> createWithKey(PatientRegistration patient, String key, String requestHash) {
		Instant now = clock.instant();
		return idempotencyStore.reserve(key, requestHash, now, now.plus(EXPIRY_HOURS, ChronoUnit.HOURS))
				.flatMap(reserved -> {
					if (!reserved) {
						// The losing request waits for the winner's transaction, then replays its response.
						return idempotencyStore.findForUpdate(key)
								.flatMap(existing -> replay(existing, requestHash))
								.switchIfEmpty(Mono.defer(() -> createWithKey(patient, key, requestHash)));
					}
					Registration registration = prepare(patient, now);
					return registration.write()
							.then(idempotencyStore.complete(key, registration.result()))
							.thenReturn(registration.result())
							.doOnNext(result -> metrics.patientClassified(result.level()))
							.doOnNext(TriageRegistrationService::logRegistered);
				});
	}

	/** Logs identifiers and the classification only; name and complaint stay out of the logs. */
	private static void logRegistered(RegistrationResult result) {
		log.info("Patient {} registered as {} with a {}-minute SLA", result.id(), result.level(),
				result.slaMinutes());
	}

	private Registration prepare(PatientRegistration patient, Instant registeredAt) {
		TriageLevel level = classifier.classify(patient);
		int slaMinutes = slaPolicy.minutesFor(level);
		UUID patientId = UUID.randomUUID();

		Patient registered = new Patient(
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
				level,
				slaMinutes,
				REGISTERED_STATUS,
				false,
				registeredAt,
				registeredAt,
				registeredAt);

		Mono<Void> write = patients.add(registered)
				.then(auditLog.append(new AuditEntry(
						UUID.randomUUID(), patientId, registeredAt, null,
						REGISTERED_STATUS, REGISTRATION_AUDIT)))
				.then(outbox.append(patientId, REGISTERED_EVENT, Map.of(
						"nivel", level.name(),
						"slaMinutos", slaMinutes,
						"estado", REGISTERED_STATUS,
						"fechaRegistro", registeredAt.toString()), registeredAt));

		return new Registration(new RegistrationResult(patientId, level, slaMinutes, REGISTERED_STATUS, registeredAt),
				write);
	}

	private record Registration(RegistrationResult result, Mono<Void> write) {
	}
}
