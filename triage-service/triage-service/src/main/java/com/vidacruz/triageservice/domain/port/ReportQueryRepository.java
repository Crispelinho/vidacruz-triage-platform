package com.vidacruz.triageservice.domain.port;

import java.time.Instant;

import com.vidacruz.triageservice.domain.dto.LatencyPercentileReport;
import com.vidacruz.triageservice.domain.dto.OperationHourReport;
import com.vidacruz.triageservice.domain.dto.SlaOperationalReport;

import reactor.core.publisher.Flux;

/** RF-05 report SQL boundary. Time ranges are already parsed and inclusive. */
public interface ReportQueryRepository {

	Flux<OperationHourReport> operationByHour(Instant from, Instant to);

	Flux<LatencyPercentileReport> latencyByLevel(Instant from, Instant to);

	Flux<SlaOperationalReport> slaOperational(Instant from, Instant to);
}
