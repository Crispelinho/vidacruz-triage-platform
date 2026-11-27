package com.vidacruz.triageservice.application.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vidacruz.triageservice.application.dto.LatencyPercentileReport;
import com.vidacruz.triageservice.application.dto.OperationHourReport;
import com.vidacruz.triageservice.application.dto.SlaOperationalReport;
import com.vidacruz.triageservice.domain.service.ReportService;

import reactor.core.publisher.Flux;

/** RF-05 SQL window/aggregate reports. */
@RestController
@RequestMapping("/triage/reports")
public class ReportController {

	private final ReportService reportService;

	public ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@GetMapping("/operation-by-hour")
	public Flux<OperationHourReport> operationByHour(
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to) {
		return reportService.operationByHour(from, to);
	}

	@GetMapping("/latency-percentiles")
	public Flux<LatencyPercentileReport> latencyPercentiles(
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to) {
		return reportService.percentileLatencyByLevel(from, to);
	}

	@GetMapping("/sla-operational")
	public Flux<SlaOperationalReport> slaOperational(
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to) {
		return reportService.operationalSla(from, to);
	}
}
