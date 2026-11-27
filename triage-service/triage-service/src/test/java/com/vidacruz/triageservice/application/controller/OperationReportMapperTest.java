package com.vidacruz.triageservice.application.controller;

import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.report.HourlyOperation;
import com.vidacruz.triageservice.domain.report.OperationReport;
import com.vidacruz.triageservice.domain.report.OperationStats;
import com.vidacruz.triageservice.domain.report.ReportPeriod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OperationReportMapperTest {

	private static final Instant FROM = Instant.parse("2026-11-25T08:00:00Z");
	private static final Instant TO = Instant.parse("2026-11-25T12:00:00Z");

	@Test
	void exposesEveryLevelWithTheContractCodesAndNullWaits() {
		var stats = OperationStats.of(1, Map.of(TriageLevel.CRITICO, 1L), 0, null, null);
		var report = new OperationReport(new ReportPeriod(FROM, TO),
				List.of(new HourlyOperation(FROM, stats)), stats);

		var response = OperationReportMapper.toResponse(report);

		assertThat(response.from()).isEqualTo(FROM);
		assertThat(response.to()).isEqualTo(TO);
		assertThat(response.hours()).hasSize(1);
		assertThat(response.hours().get(0).registeredByLevel()).containsExactlyInAnyOrderEntriesOf(Map.of(
				"CRITICO", 1L, "URGENTE", 0L, "URGENTE_2", 0L, "NO_URGENTE", 0L, "NO_URGENTE_2", 0L));
		assertThat(response.hours().get(0).averageWaitMinutes()).isNull();
		assertThat(response.summary().registered()).isEqualTo(1);
	}

	@Test
	void exposesTheWholeRangePercentileComputedOverPatients() {
		var stats = OperationStats.of(2, Map.of(), 2,
				new BigDecimal("10.50"), new BigDecimal("21.00"));

		var response = OperationReportMapper.toResponse(new OperationReport(new ReportPeriod(FROM, TO), List.of(), stats));

		assertThat(response.summary().averageWaitMinutes()).isEqualByComparingTo("10.50");
		assertThat(response.summary().p95WaitMinutes()).isEqualByComparingTo("21.00");
		assertThat(response.summary().attended()).isEqualTo(2);
	}
}
