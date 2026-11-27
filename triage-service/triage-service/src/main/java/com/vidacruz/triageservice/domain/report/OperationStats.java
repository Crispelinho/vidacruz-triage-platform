package com.vidacruz.triageservice.domain.report;

import com.vidacruz.triageservice.domain.model.TriageLevel;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Figures of RF-05 for one UTC hour or for the whole range. The wait is the time from registration to the first
 * {@code EN_CONSULTA}; patients who never entered consultation are counted but excluded from the wait statistics.
 *
 * @param registered         patients registered
 * @param registeredByLevel  patients registered per triage level, every level included
 * @param attended           registered patients who entered consultation
 * @param averageWaitMinutes average wait in minutes, null when nobody was attended
 * @param p95WaitMinutes     95th percentile of the wait in minutes, null when nobody was attended
 */
public record OperationStats(long registered, Map<TriageLevel, Long> registeredByLevel, long attended,
                             BigDecimal averageWaitMinutes, BigDecimal p95WaitMinutes) {

	public OperationStats {
		var levels = new EnumMap<TriageLevel, Long>(TriageLevel.class);
		levels.putAll(registeredByLevel);
		registeredByLevel = Collections.unmodifiableMap(levels);
	}

	public static OperationStats of(long registered, Map<TriageLevel, Long> byLevel, long attended,
	                                BigDecimal averageWaitMinutes, BigDecimal p95WaitMinutes) {
		return new OperationStats(registered, byLevel, attended, averageWaitMinutes, p95WaitMinutes);
	}
}
