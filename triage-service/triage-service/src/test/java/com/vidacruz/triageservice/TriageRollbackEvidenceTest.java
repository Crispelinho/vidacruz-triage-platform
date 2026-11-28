package com.vidacruz.triageservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.vidacruz.triageservice.domain.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.domain.service.TriageRegistrationService;
import com.vidacruz.triageservice.event.infrastructure.OutboxEventWriter;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Forced-failure evidence: the patient, audit, outbox and idempotency writes roll back together. */
@Import(TriageTestContainers.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TriageRollbackEvidenceTest {

	@Autowired
	private DatabaseClient databaseClient;

	@Autowired
	private TriageRegistrationService registrationService;

	@MockitoBean
	private OutboxEventWriter eventWriter;

	@BeforeEach
	void cleanDatabase() {
		databaseClient.sql("DELETE FROM idempotency_key").then().block();
		databaseClient.sql("DELETE FROM auditoria_clinica").then().block();
		databaseClient.sql("DELETE FROM outbox_evento").then().block();
		databaseClient.sql("DELETE FROM paciente").then().block();
	}

	@Test
	void failedEventWriteRollsBackTheWholeRegistration() {
		when(eventWriter.write(any(UUID.class), anyString(), anyMap(), any(Instant.class)))
				.thenReturn(Mono.error(new IllegalStateException("forced outbox failure")));

		StepVerifier.create(registrationService.register(request(), "rollback-key"))
				.expectErrorMessage("forced outbox failure")
				.verify();

		assertThat(count("SELECT COUNT(*) AS n FROM idempotency_key")).isZero();
		assertThat(count("SELECT COUNT(*) AS n FROM paciente")).isZero();
		assertThat(count("SELECT COUNT(*) AS n FROM auditoria_clinica")).isZero();
		assertThat(count("SELECT COUNT(*) AS n FROM outbox_evento")).isZero();
	}

	private static PatientRegistrationRequest request() {
		return new PatientRegistrationRequest(
				"Ana Perez", 45, 80, 120, 80, 98, new BigDecimal("36.5"), 2, 15, "dolor de pecho");
	}

	private long count(String sql) {
		Long result = databaseClient.sql(sql)
				.map((row, metadata) -> row.get("n", Long.class))
				.one().block();
		return result == null ? 0L : result;
	}
}
