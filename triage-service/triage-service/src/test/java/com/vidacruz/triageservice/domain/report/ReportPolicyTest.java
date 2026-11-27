package com.vidacruz.triageservice.domain.report;

import com.vidacruz.triageservice.domain.exception.InvalidReportRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportPolicyTest {

	private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
	private static final ReportPolicy POLICY = new ReportPolicy(Duration.ofDays(90));

	@Test
	void acceptsExactlyNinetyNaturalDates() {
		var hasta = FROM.plus(89, ChronoUnit.DAYS).plusSeconds(12);

		assertThat(POLICY.periodBetween(FROM, hasta)).isEqualTo(new ReportPeriod(FROM, hasta));
	}

	@Test
	void rejectsNinetyOneNaturalDatesIncludingTheEnds() {
		var hasta = FROM.plus(90, ChronoUnit.DAYS);

		assertThatThrownBy(() -> POLICY.periodBetween(FROM, hasta))
				.isInstanceOfSatisfying(InvalidReportRequestException.class, error -> {
					assertThat(error.code()).isEqualTo(InvalidReportRequestException.RANGE_CODE);
					assertThat(error.field()).isEqualTo("hasta");
					assertThat(error).hasMessage("el rango no puede superar 90 días");
				});
	}

	@Test
	void rejectsAnInvertedOrEmptyRangeOnDesde() {
		assertThatThrownBy(() -> POLICY.periodBetween(FROM, FROM))
				.isInstanceOfSatisfying(InvalidReportRequestException.class, error -> {
					assertThat(error.code()).isEqualTo(InvalidReportRequestException.RANGE_CODE);
					assertThat(error.field()).isEqualTo("desde");
					assertThat(error).hasMessage("la fecha desde debe ser anterior a la fecha hasta");
				});
		assertThatThrownBy(() -> POLICY.periodBetween(FROM.plusSeconds(1), FROM))
				.isInstanceOf(InvalidReportRequestException.class);
	}

	@Test
	void rejectsTheSameNaturalDateWhenDesdeIsLaterThanHasta() {
		assertThatThrownBy(() -> POLICY.periodBetween(FROM.plusSeconds(3600), FROM))
				.isInstanceOf(InvalidReportRequestException.class);
	}

	@ParameterizedTest
	@CsvSource({"PT12H, 12 horas", "PT90M, 90 minutos", "P2D, 2 días", "PT25H, 25 horas"})
	void describesTheMaximumInItsLargestWholeUnit(Duration maximum, String described) {
		var policy = new ReportPolicy(maximum);
		var hasta = FROM.plus(maximum).plusSeconds(1);

		assertThatThrownBy(() -> policy.periodBetween(FROM, hasta))
				.isInstanceOfSatisfying(InvalidReportRequestException.class,
						error -> assertThat(error).hasMessage("el rango no puede superar " + described));
	}

	@Test
	void requiresAPositiveMaximumAndBothEnds() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ReportPolicy(null));
		assertThatIllegalArgumentException().isThrownBy(() -> new ReportPolicy(Duration.ZERO));
		assertThatIllegalArgumentException().isThrownBy(() -> new ReportPolicy(Duration.ofHours(-1)));
		assertThatThrownBy(() -> POLICY.periodBetween(null, FROM)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> POLICY.periodBetween(FROM, null)).isInstanceOf(NullPointerException.class);
	}
}
