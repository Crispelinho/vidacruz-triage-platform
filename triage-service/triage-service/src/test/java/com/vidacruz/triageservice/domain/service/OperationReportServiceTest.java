package com.vidacruz.triageservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vidacruz.triageservice.application.dto.OperationReportBucket;
import com.vidacruz.triageservice.domain.exception.InvalidReportRangeException;
import com.vidacruz.triageservice.infrastructure.persistence.OperationReportRepository;
import com.vidacruz.triageservice.infrastructure.persistence.OperationReportRow;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OperationReportServiceTest {

	@Mock
	private OperationReportRepository repository;

	private OperationReportService service;

	@BeforeEach
	void setUp() {
		service = new OperationReportService(repository);
	}

	@Test
	void acceptsNinetyCalendarDaysAndMapsTheAggregatedRow() {
		OffsetDateTime hora = OffsetDateTime.of(2026, 11, 24, 16, 0, 0, 0, ZoneOffset.UTC);
		OperationReportRow row = new OperationReportRow(hora, 4, 1, 1, 1, 1, 0,
				new BigDecimal("7.5"), new BigDecimal("12.0"));
		when(repository.findHourly(any(Instant.class), any(Instant.class))).thenReturn(Flux.just(row));

		StepVerifier.create(service.hourly("2026-09-01T00:00:00Z", "2026-11-29T00:00:00Z"))
				.assertNext(bucket -> {
					assertThat(bucket.hora()).isEqualTo("2026-11-24T16:00:00Z");
					assertThat(bucket.totalRegistrados()).isEqualTo(4);
					assertThat(bucket.porNivel())
							.containsEntry("CRITICO", 1L)
							.containsEntry("URGENTE_2", 1L)
							.containsEntry("NO_URGENTE_2", 0L);
					assertThat(bucket.promedioEsperaMinutos()).isEqualByComparingTo("7.5");
					assertThat(bucket.p95EsperaMinutos()).isEqualByComparingTo("12.0");
				})
				.verifyComplete();

		ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
		verify(repository).findHourly(org.mockito.ArgumentMatchers.eq(
				Instant.parse("2026-09-01T00:00:00Z")), end.capture());
		assertThat(end.getValue()).isEqualTo(Instant.parse("2026-11-30T00:00:00Z"));
	}

	@Test
	void rejectsReversedRangeOnDesde() {
		assertThatThrownBy(() -> service.hourly("2026-11-30T00:00:00Z", "2026-11-29T00:00:00Z"))
				.isInstanceOf(InvalidReportRangeException.class)
				.satisfies(error -> assertThat(((InvalidReportRangeException) error).field()).isEqualTo("desde"));
	}

	@Test
	void rejectsNinetyOneCalendarDaysOnHasta() {
		assertThatThrownBy(() -> service.hourly("2026-09-01T00:00:00Z", "2026-11-30T00:00:00Z"))
				.isInstanceOf(InvalidReportRangeException.class)
				.satisfies(error -> assertThat(((InvalidReportRangeException) error).field()).isEqualTo("hasta"));
	}

	@Test
	void rejectsUnparseableRangeOnTheOffendingParameter() {
		assertThatThrownBy(() -> service.hourly("not-a-date", "2026-11-29T00:00:00Z"))
				.isInstanceOf(InvalidReportRangeException.class)
				.satisfies(error -> assertThat(((InvalidReportRangeException) error).field()).isEqualTo("desde"));
	}

	@Test
	void keepsNullWaitMetricsWhenNobodyEnteredConsultation() {
		OffsetDateTime hora = OffsetDateTime.of(2026, 11, 24, 16, 0, 0, 0, ZoneOffset.UTC);
		when(repository.findHourly(any(Instant.class), any(Instant.class)))
				.thenReturn(Flux.just(new OperationReportRow(hora, 1, 1, 0, 0, 0, 0, null, null)));

		StepVerifier.create(service.hourly("2026-11-24T00:00:00Z", "2026-11-24T23:00:00Z"))
				.assertNext(bucket -> {
					assertThat(bucket.promedioEsperaMinutos()).isNull();
					assertThat(bucket.p95EsperaMinutos()).isNull();
				})
				.verifyComplete();
	}
}
