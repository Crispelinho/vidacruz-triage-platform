package com.vidacruz.triageservice.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.TriageMetrics;
import com.vidacruz.triageservice.domain.report.OperationReportRepository;
import com.vidacruz.triageservice.domain.report.ReportPolicy;
import com.vidacruz.triageservice.domain.service.AcknowledgeAlertUseCase;
import com.vidacruz.triageservice.domain.service.GetOperationReportUseCase;
import com.vidacruz.triageservice.domain.service.ListActiveAlertsUseCase;
import com.vidacruz.triageservice.domain.service.PatientLifecycleService;
import com.vidacruz.triageservice.domain.service.ReportService;
import com.vidacruz.triageservice.domain.service.SlaEvaluator;
import com.vidacruz.triageservice.domain.service.TriageClassifier;
import com.vidacruz.triageservice.domain.service.TriageRegistrationService;
import com.vidacruz.triageservice.domain.service.WatchAlertsUseCase;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventWriter;
import com.vidacruz.triageservice.infrastructure.persistence.AuditEntryRepository;
import com.vidacruz.triageservice.infrastructure.persistence.IdempotencyKeyRepository;
import com.vidacruz.triageservice.infrastructure.persistence.PatientRepository;

/**
 * Explicit wiring for the domain services. Keeping the beans here lets the use cases stay free of Spring DI
 * annotations; the constructors and every transaction boundary remain unchanged.
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
	public SlaEvaluator slaEvaluator(PatientRepository patientRepository,
			AuditEntryRepository auditRepository,
			OutboxEventWriter eventWriter,
			MedicalAlertRepository alertRepository,
			TransactionalOperator transactionalOperator,
			Clock clock,
			TriageMetrics metrics) {
		return new SlaEvaluator(patientRepository, auditRepository, eventWriter, alertRepository,
				transactionalOperator, clock, metrics);
	}

	@Bean
	public TriageRegistrationService triageRegistrationService(PatientRepository patientRepository,
			AuditEntryRepository auditRepository,
			OutboxEventWriter eventWriter,
			IdempotencyKeyRepository idempotencyRepository,
			ObjectMapper objectMapper,
			TriageClassifier classifier,
			SlaProperties slaProperties,
			TransactionalOperator transactionalOperator,
			Clock clock,
			TriageMetrics metrics) {
		return new TriageRegistrationService(patientRepository, auditRepository, eventWriter, idempotencyRepository,
				objectMapper, classifier, slaProperties, transactionalOperator, clock, metrics);
	}

	@Bean
	public PatientLifecycleService patientLifecycleService(PatientRepository patientRepository,
			AuditEntryRepository auditRepository,
			OutboxEventWriter eventWriter,
			MedicalAlertRepository alertRepository,
			TransactionalOperator transactionalOperator,
			Clock clock) {
		return new PatientLifecycleService(patientRepository, auditRepository, eventWriter, alertRepository,
				transactionalOperator, clock);
	}

	@Bean
	public ReportService reportService(DatabaseClient databaseClient) {
		return new ReportService(databaseClient);
	}
}
