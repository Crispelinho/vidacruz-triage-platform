package com.vidacruz.triageservice.domain.service;

import com.vidacruz.triageservice.domain.exception.InvalidReportRequestException;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.report.OperationReport;
import com.vidacruz.triageservice.domain.report.OperationStats;
import com.vidacruz.triageservice.domain.report.OperationReportRepository;
import com.vidacruz.triageservice.domain.report.ReportPeriod;
import com.vidacruz.triageservice.domain.report.ReportPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOperationReportUseCaseTest {

	private static final Instant DESDE = Instant.parse("2026-11-25T08:00:00Z");
	private static final Instant HASTA = Instant.parse("2026-11-25T12:00:00Z");

	@Mock
	private OperationReportRepository repository;

	@Test
	void computesTheReportOfAValidRange() {
		var period = new ReportPeriod(DESDE, HASTA);
		var stats = OperationStats.of(1, Map.of(TriageLevel.CRITICO, 1L), 0, null, null);
		var report = new OperationReport(period, List.of(), stats);
		when(repository.operationReport(period)).thenReturn(Mono.just(report));

		StepVerifier.create(useCase().getOperationReport(DESDE, HASTA))
				.expectNext(report)
				.verifyComplete();
	}

	@Test
	void rejectsAnInvalidRangeWithoutQuerying() {
		StepVerifier.create(useCase().getOperationReport(HASTA, DESDE))
				.expectError(InvalidReportRequestException.class)
				.verify();
		StepVerifier.create(useCase().getOperationReport(DESDE, DESDE.plus(Duration.ofDays(91))))
				.expectError(InvalidReportRequestException.class)
				.verify();

		verifyNoInteractions(repository);
	}

	private GetOperationReportUseCase useCase() {
		return new GetOperationReportUseCase(repository, new ReportPolicy(Duration.ofDays(90)));
	}
}
