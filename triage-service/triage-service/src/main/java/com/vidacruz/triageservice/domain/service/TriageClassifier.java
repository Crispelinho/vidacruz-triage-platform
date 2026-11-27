package com.vidacruz.triageservice.domain.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

import com.vidacruz.triageservice.domain.model.PatientRegistration;
import com.vidacruz.triageservice.domain.model.TriageLevel;

/** Deterministic RN-01 classifier: the first matching level in severity order wins. */
public class TriageClassifier {

	private static final Set<String> ADMINISTRATIVE_COMPLAINTS = Set.of(
			"documentos",
			"recoleccion de resultados",
			"renovacion de recetario",
			"vacunacion",
			"seguimiento");

	public TriageLevel classify(PatientRegistration patient) {
		if (patient.heartRate() < 40 || patient.heartRate() > 120
				|| patient.spo2() < 90
				|| patient.systolic() < 90
				|| patient.gcs() <= 8) {
			return TriageLevel.CRITICO;
		}
		if (patient.spo2() >= 90 && patient.spo2() <= 93
				|| patient.systolic() >= 90 && patient.systolic() <= 100
				|| patient.temperature() >= 38.5
				|| patient.gcs() >= 9 && patient.gcs() <= 13) {
			return TriageLevel.URGENTE;
		}
		if (patient.heartRate() >= 90 && patient.heartRate() <= 120
				|| patient.temperature() >= 38.0 && patient.temperature() < 38.5
				|| patient.pain() >= 7) {
			return TriageLevel.URGENTE_2;
		}
		return ADMINISTRATIVE_COMPLAINTS.contains(normalize(patient.complaint()))
				? TriageLevel.NO_URGENTE_2
				: TriageLevel.NO_URGENTE;
	}

	private static String normalize(String value) {
		String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
		String withoutAccents = decomposed.replaceAll("\\p{M}", "");
		return withoutAccents.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
	}
}
