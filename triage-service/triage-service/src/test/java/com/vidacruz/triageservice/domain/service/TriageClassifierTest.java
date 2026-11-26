package com.vidacruz.triageservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.vidacruz.triageservice.domain.exception.InvalidPatientDataException;
import com.vidacruz.triageservice.domain.model.PatientRegistration;
import com.vidacruz.triageservice.domain.model.TriageLevel;

class TriageClassifierTest {

	private final TriageClassifier classifier = new TriageClassifier();

	@ParameterizedTest(name = "{0}")
	@MethodSource("specificationCases")
	void classifiesTheSixSpecificationExamples(String label, PatientRegistration patient, TriageLevel expected) {
		assertThat(classifier.classify(patient)).isEqualTo(expected);
	}

	private static Stream<Arguments> specificationCases() {
		return Stream.of(
				Arguments.of("E1", registration(130, 180, 110, 97, 36.8, 8, 15, "Dolor torácico"), TriageLevel.CRITICO),
				Arguments.of("E2", registration(95, 150, 95, 91, 37.0, 4, 15, "Dificultad para respirar"), TriageLevel.URGENTE),
				Arguments.of("E3", registration(88, 130, 85, 98, 38.2, 6, 15, "Fiebre"), TriageLevel.URGENTE_2),
				Arguments.of("E4", registration(72, 120, 80, 99, 36.5, 2, 15, "Herida por cortar"), TriageLevel.NO_URGENTE),
				Arguments.of("E5", registration(70, 115, 75, 99, 36.4, 0, 15, "Renovación de recetario"), TriageLevel.NO_URGENTE_2),
				Arguments.of("E6", registration(35, 80, 50, 85, 35.9, 0, 6, "Inconsciente"), TriageLevel.CRITICO));
	}

	@Test
	void rejectsOutOfRangeValuesWithTheFieldName() {
		assertThatThrownBy(() -> registration(19, 180, 110, 97, 36.8, 8, 15, "Dolor torácico"))
				.isInstanceOf(InvalidPatientDataException.class)
				.satisfies(error -> assertThat(((InvalidPatientDataException) error).errors())
						.anySatisfy(field -> assertThat(field.campo()).isEqualTo("frecuencia_cardiaca")));
		assertThatThrownBy(() -> registration(72, 180, 185, 97, 36.8, 8, 15, "Dolor torácico"))
				.isInstanceOf(InvalidPatientDataException.class)
				.satisfies(error -> assertThat(((InvalidPatientDataException) error).errors())
						.anySatisfy(field -> assertThat(field.campo()).isEqualTo("presion_diastolica")));
	}

	private static PatientRegistration registration(int heartRate, int systolic, int diastolic, int spo2,
			double temperature, int pain, int gcs, String complaint) {
		return new PatientRegistration("Paciente de prueba", 40, heartRate, systolic, diastolic, spo2,
				temperature, pain, gcs, complaint);
	}
}
