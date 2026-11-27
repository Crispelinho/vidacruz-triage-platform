package com.vidacruz.triageservice.application.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
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
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return reportService.operationByHour(from, to);
	}

	@GetMapping("/latency-percentiles")
	public Flux<LatencyPercentileReport> latencyPercentiles() {
		return reportService.percentileLatencyByLevel();
	}

	@GetMapping("/sla-operational")
	public Flux<SlaOperationalReport> slaOperational() {
		return reportService.operationalSla();
	}
}
