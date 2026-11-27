package com.vidacruz.triageservice.application.controller;

import com.vidacruz.triageservice.application.dto.HourlyOperationResponse;
import com.vidacruz.triageservice.application.dto.OperationReportResponse;
import com.vidacruz.triageservice.application.dto.OperationSummaryResponse;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.report.HourlyOperation;
import com.vidacruz.triageservice.domain.report.OperationReport;
import com.vidacruz.triageservice.domain.report.OperationStats;

import java.util.LinkedHashMap;
import java.util.Map;

/** Translates the report to the Spanish HTTP contract, with all levels in severity order. */
final class OperationReportMapper {

	static OperationReportResponse toResponse(OperationReport report) {
		var hours = report.hours().stream().map(OperationReportMapper::toHourly).toList();
		var summary = report.summary();
		return new OperationReportResponse(
				report.period().from(), report.period().to(), hours,
				new OperationSummaryResponse(summary.registered(), byLevel(summary), summary.attended(),
						summary.averageWaitMinutes(), summary.p95WaitMinutes()));
	}

	private static HourlyOperationResponse toHourly(HourlyOperation hour) {
		var stats = hour.stats();
		return new HourlyOperationResponse(hour.hour(), stats.registered(), byLevel(stats), stats.attended(),
				stats.averageWaitMinutes(), stats.p95WaitMinutes());
	}

	private static Map<String, Long> byLevel(OperationStats stats) {
		var levels = new LinkedHashMap<String, Long>();
		for (var level : TriageLevel.values()) {
			levels.put(level.name(), stats.registeredByLevel().getOrDefault(level, 0L));
		}
		return levels;
	}

	private OperationReportMapper() {
	}
}
