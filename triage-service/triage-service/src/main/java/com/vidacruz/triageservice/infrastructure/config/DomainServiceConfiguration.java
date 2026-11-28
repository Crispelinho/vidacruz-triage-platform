package com.vidacruz.triageservice.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.vidacruz.triageservice.domain.model.AuditLog;
import com.vidacruz.triageservice.domain.model.EventOutbox;
import com.vidacruz.triageservice.domain.model.IdempotencyStore;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.PatientStore;
import com.vidacruz.triageservice.domain.model.RequestFingerprinter;
import com.vidacruz.triageservice.domain.model.SlaPolicy;
import com.vidacruz.triageservice.domain.model.TransactionRunner;
import com.vidacruz.triageservice.domain.model.TriageMetrics;
import com.vidacruz.triageservice.domain.report.OperationReportRepository;
import com.vidacruz.triageservice.domain.report.ReportPolicy;
import com.vidacruz.triageservice.domain.service.AcknowledgeAlertUseCase;
import com.vidacruz.triageservice.domain.service.GetOperationReportUseCase;
import com.vidacruz.triageservice.domain.service.ListActiveAlertsUseCase;
import com.vidacruz.triageservice.domain.service.PatientLifecycleService;
import com.vidacruz.triageservice.domain.service.SlaEvaluator;
import com.vidacruz.triageservice.domain.service.TriageClassifier;
import com.vidacruz.triageservice.domain.service.TriageRegistrationService;
import com.vidacruz.triageservice.domain.service.WatchAlertsUseCase;

/**
 * Explicit wiring for the domain services. The use cases depend only on domain ports; the adapters that implement
 * them live in infrastructure, so the domain stays free of Spring, Jackson and persistence types.
 */
@Configuration
public class DomainServiceConfiguration {

	@Bean
	public AcknowledgeAlertUseCase acknowledgeAlertUseCase(MedicalAlertRepository alertRepository,
			TriageMetrics metrics) {
		return new AcknowledgeAlertUseCase(alertRepository, metrics);
	}

	@Bean
	public GetOperationReportUseCase getOperationReportUseCase(OperationReportRepository operationReportRepository,
			ReportPolicy reportPolicy) {
		return new GetOperationReportUseCase(operationReportRepository, reportPolicy);
	}

	@Bean
	public ListActiveAlertsUseCase listActiveAlertsUseCase(MedicalAlertRepository alertRepository) {
		return new ListActiveAlertsUseCase(alertRepository);
	}

	@Bean
	public WatchAlertsUseCase watchAlertsUseCase(MedicalAlertRepository alertRepository) {
		return new WatchAlertsUseCase(alertRepository);
	}

	@Bean
	public TriageClassifier triageClassifier() {
		return new TriageClassifier();
	}

	@Bean
	public SlaEvaluator slaEvaluator(PatientStore patients,
			AuditLog auditLog,
			EventOutbox outbox,
			MedicalAlertRepository alertRepository,
			TransactionRunner transactions,
			Clock clock,
			TriageMetrics metrics) {
		return new SlaEvaluator(patients, auditLog, outbox, alertRepository, transactions, clock, metrics);
	}

	@Bean
	public TriageRegistrationService triageRegistrationService(PatientStore patients,
			AuditLog auditLog,
			EventOutbox outbox,
			IdempotencyStore idempotencyStore,
			RequestFingerprinter fingerprinter,
			TriageClassifier classifier,
			SlaPolicy slaPolicy,
			TransactionRunner transactions,
			Clock clock,
			TriageMetrics metrics) {
		return new TriageRegistrationService(patients, auditLog, outbox, idempotencyStore, fingerprinter, classifier,
				slaPolicy, transactions, clock, metrics);
	}

	@Bean
	public PatientLifecycleService patientLifecycleService(PatientStore patients,
			AuditLog auditLog,
			EventOutbox outbox,
			MedicalAlertRepository alertRepository,
			TransactionRunner transactions,
			Clock clock) {
		return new PatientLifecycleService(patients, auditLog, outbox, alertRepository, transactions, clock);
	}
}
