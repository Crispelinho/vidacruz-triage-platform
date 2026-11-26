package com.vidacruz.triageservice.domain.report;

import java.time.Instant;

/**
 * Figures of the patients registered during one UTC hour, including hours without patients.
 *
 * @param hour  start of the hour
 * @param stats figures of the patients registered in that hour
 */
public record HourlyOperation(Instant hour, OperationStats stats) {
}
