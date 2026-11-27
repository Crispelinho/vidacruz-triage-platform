package com.vidacruz.triageservice.infrastructure.report;

import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.report.OperationStats;
import com.vidacruz.triageservice.domain.report.ReportPeriod;
import io.r2dbc.spi.Readable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationReportRowsTest {

	private static final Instant HOUR = Instant.parse("2026-11-25T08:00:00Z");

	@Mock
	private Readable row;

	@Test
	void mapsEveryLevelAndTheWaitFigures() {
		when(row.get("hour", Instant.class)).thenReturn(HOUR);
		when(row.get("whole_range", Boolean.class)).thenReturn(false);
		when(row.get("registered", Long.class)).thenReturn(3L);
		when(row.get("critico", Long.class)).thenReturn(1L);
		when(row.get("urgente", Long.class)).thenReturn(2L);
		when(row.get("urgente_2", Long.class)).thenReturn(0L);
		when(row.get("no_urgente", Long.class)).thenReturn(0L);
		when(row.get("no_urgente_2", Long.class)).thenReturn(0L);
		when(row.get("attended", Long.class)).thenReturn(2L);
		when(row.get("promedio_espera_minutos", BigDecimal.class)).thenReturn(new BigDecimal("12.40"));
		when(row.get("p95_espera_minutos", BigDecimal.class)).thenReturn(null);

		var mapped = OperationReportRows.toRow(row);

		assertThat(mapped.hour()).isEqualTo(HOUR);
		assertThat(mapped.wholeRange()).isFalse();
		assertThat(mapped.stats().registered()).isEqualTo(3);
		assertThat(mapped.stats().registeredByLevel())
				.containsEntry(TriageLevel.CRITICO, 1L)
				.containsEntry(TriageLevel.URGENTE, 2L)
				.containsEntry(TriageLevel.URGENTE_2, 0L);
		assertThat(mapped.stats().attended()).isEqualTo(2);
		assertThat(mapped.stats().averageWaitMinutes()).isEqualByComparingTo("12.40");
		assertThat(mapped.stats().p95WaitMinutes()).isNull();
	}

	@Test
	void keepsHoursInOrderAndSeparatesTheWholeRangeSummary() {
		var period = new ReportPeriod(HOUR, HOUR.plusSeconds(7200));
		var hourStats = OperationStats.of(0, Map.of(), 0, null, null);
		var rows = List.of(
				new OperationReportRows.ReportRow(HOUR, false, hourStats),
				new OperationReportRows.ReportRow(HOUR.plusSeconds(3600), false, hourStats),
				new OperationReportRows.ReportRow(null, true, hourStats));

		var report = OperationReportRows.toReport(period, rows);

		assertThat(report.period()).isEqualTo(period);
		assertThat(report.hours()).extracting("hour").containsExactly(HOUR, HOUR.plusSeconds(3600));
		assertThat(report.summary()).isSameAs(hourStats);
	}

	@Test
	void exclusiveEndIsTheStartOfTheDayAfterHasta() {
		var period = new ReportPeriod(HOUR, Instant.parse("2026-11-25T20:30:00Z"));

		assertThat(PostgresOperationReportRepository.exclusiveEnd(period))
				.isEqualTo(Instant.parse("2026-11-26T00:00:00Z"));
	}
}
