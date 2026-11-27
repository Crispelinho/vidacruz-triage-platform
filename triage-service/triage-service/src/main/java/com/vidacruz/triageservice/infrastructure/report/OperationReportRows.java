package com.vidacruz.triageservice.infrastructure.report;

import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.report.HourlyOperation;
import com.vidacruz.triageservice.domain.report.OperationReport;
import com.vidacruz.triageservice.domain.report.OperationStats;
import com.vidacruz.triageservice.domain.report.ReportPeriod;
import io.r2dbc.spi.Readable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/** Maps the rows of {@link OperationReportSql#HOURLY_WITH_SUMMARY}. No figure is calculated here. */
final class OperationReportRows {

	record ReportRow(Instant hour, boolean wholeRange, OperationStats stats) {
	}

	static ReportRow toRow(Readable row) {
		return new ReportRow(
				row.get("hour", Instant.class),
				Boolean.TRUE.equals(row.get("whole_range", Boolean.class)),
				OperationStats.of(
						row.get("registered", Long.class),
						byLevel(row),
						row.get("attended", Long.class),
						row.get("promedio_espera_minutos", BigDecimal.class),
						row.get("p95_espera_minutos", BigDecimal.class)));
	}

	/** The query returns hours first and exactly one whole-range row at the end. */
	static OperationReport toReport(ReportPeriod period, List<ReportRow> rows) {
		var hours = new ArrayList<HourlyOperation>();
		OperationStats summary = null;
		for (var row : rows) {
			if (row.wholeRange()) {
				summary = row.stats();
			} else {
				hours.add(new HourlyOperation(row.hour(), row.stats()));
			}
		}
		return new OperationReport(period, hours, summary);
	}

	/** Each level has its own column, named after the enum constant in lower case. */
	private static EnumMap<TriageLevel, Long> byLevel(Readable row) {
		var levels = new EnumMap<TriageLevel, Long>(TriageLevel.class);
		for (var level : TriageLevel.values()) {
			levels.put(level, row.get(level.name().toLowerCase(Locale.ROOT), Long.class));
		}
		return levels;
	}

	private OperationReportRows() {
	}
}
