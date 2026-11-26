package com.vidacruz.triageservice.domain.report;

import java.util.List;

/**
 * Operation report of RF-05: one entry per hour of the range and the figures of the whole range. The summary is
 * computed over the patients, because hourly percentiles cannot be combined.
 *
 * @param period  requested range
 * @param hours   figures per hour, in order
 * @param summary figures of the whole range
 */
public record OperationReport(ReportPeriod period, List<HourlyOperation> hours, OperationStats summary) {

	public OperationReport {
		hours = List.copyOf(hours);
	}
}
