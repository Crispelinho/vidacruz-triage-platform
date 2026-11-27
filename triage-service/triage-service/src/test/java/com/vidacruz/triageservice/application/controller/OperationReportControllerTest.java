package com.vidacruz.triageservice.application.controller;

import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.report.HourlyOperation;
import com.vidacruz.triageservice.domain.report.OperationReport;
import com.vidacruz.triageservice.domain.report.OperationReportRepository;
import com.vidacruz.triageservice.domain.report.OperationStats;
import com.vidacruz.triageservice.domain.report.ReportPeriod;
import com.vidacruz.triageservice.domain.report.ReportPolicy;
import com.vidacruz.triageservice.domain.service.GetOperationReportUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = OperationReportController.class)
@Import({ GetOperationReportUseCase.class, OperationReportControllerTest.TestPolicy.class,
		OperationReportControllerTest.TestSecurity.class })
class OperationReportControllerTest {

	private static final String DESDE = "2026-11-25T08:00:00Z";
	private static final String HASTA = "2026-11-25T12:00:00Z";

	@MockitoBean
	private OperationReportRepository repository;

	@Autowired
	private WebTestClient client;

	@Test
	void returnsHourlyBucketsAndWholeRangeSummary() {
		var from = Instant.parse(DESDE);
		var stats = OperationStats.of(2, Map.of(TriageLevel.CRITICO, 1L), 1,
				new BigDecimal("12.40"), new BigDecimal("31.00"));
		when(repository.operationReport(any(ReportPeriod.class))).thenReturn(Mono.just(
				new OperationReport(new ReportPeriod(from, Instant.parse(HASTA)),
						List.of(new HourlyOperation(from, stats)), stats)));

		report(DESDE, HASTA)
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.desde").isEqualTo(DESDE)
				.jsonPath("$.hasta").isEqualTo(HASTA)
				.jsonPath("$.horas.length()").isEqualTo(1)
				.jsonPath("$.horas[0].hora").isEqualTo(DESDE)
				.jsonPath("$.horas[0].totalRegistrados").isEqualTo(2)
				.jsonPath("$.horas[0].porNivel.CRITICO").isEqualTo(1)
				.jsonPath("$.horas[0].porNivel.NO_URGENTE_2").isEqualTo(0)
				.jsonPath("$.horas[0].promedioEsperaMinutos").isEqualTo(12.4)
				.jsonPath("$.resumen.p95EsperaMinutos").isEqualTo(31.0)
				.jsonPath("$.resumen.totalRegistrados").isEqualTo(2);
	}

	@Test
	void rejectsAMissingParameterWithItsField() {
		report(null, HASTA)
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
				.jsonPath("$.field").isEqualTo("desde");

		verifyNoInteractions(repository);
	}

	@Test
	void rejectsADateWithoutAnOffset() {
		report("2026-11-25T08:00:00", HASTA)
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
				.jsonPath("$.field").isEqualTo("desde");

		verifyNoInteractions(repository);
	}

	@Test
	void rejectsAnInvertedRangeWithoutQuerying() {
		report(HASTA, DESDE)
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.code").isEqualTo("INVALID_REPORT_RANGE")
				.jsonPath("$.field").isEqualTo("desde");

		verifyNoInteractions(repository);
	}

	@Test
	void rejectsNinetyOneNaturalDates() {
		report("2026-01-01T00:00:00Z", "2026-04-01T00:00:00Z")
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.field").isEqualTo("hasta");

		verifyNoInteractions(repository);
	}

	private WebTestClient.ResponseSpec report(String desde, String hasta) {
		var uri = org.springframework.web.util.UriComponentsBuilder.fromPath("/triage/reportes/operacion");
		if (desde != null) {
			uri.queryParam("desde", desde);
		}
		if (hasta != null) {
			uri.queryParam("hasta", hasta);
		}
		return client.get().uri(uri.build().toUriString()).exchange();
	}

	@TestConfiguration
	static class TestPolicy {

		@Bean
		ReportPolicy reportPolicy() {
			return new ReportPolicy(Duration.ofDays(90));
		}
	}

	@TestConfiguration
	static class TestSecurity {

		@Bean
		SecurityWebFilterChain permitAll(ServerHttpSecurity http) {
			return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
					.authorizeExchange(exchange -> exchange.anyExchange().permitAll())
					.build();
		}
	}
}
