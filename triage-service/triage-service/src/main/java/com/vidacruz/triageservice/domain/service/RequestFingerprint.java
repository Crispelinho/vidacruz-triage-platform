package com.vidacruz.triageservice.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.domain.model.PatientRegistration;

/** SHA-256 of the canonical, validated request body: sorted wire keys and normalized values (DEC-15, ADR-12). */
public final class RequestFingerprint {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private RequestFingerprint() {
	}

	public static String of(PatientRegistration registration) {
		Map<String, Object> canonical = new TreeMap<>();
		canonical.put("edad", registration.age());
		canonical.put("dolor", registration.pain());
		canonical.put("frecuencia_cardiaca", registration.heartRate());
		canonical.put("gcs", registration.gcs());
		canonical.put("nombre_completo", registration.fullName().trim());
		canonical.put("presion_diastolica", registration.diastolic());
		canonical.put("presion_sistolica", registration.systolic());
		canonical.put("queja_principal", registration.complaint().trim());
		canonical.put("spo2", registration.spo2());
		canonical.put("temperatura", registration.temperature());
		try {
			byte[] bytes = MAPPER.writeValueAsBytes(canonical);
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (JsonProcessingException | NoSuchAlgorithmException exception) {
			throw new IllegalStateException("Cannot build canonical request digest", exception);
		}
	}

	public static String of(String canonicalJson) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(canonicalJson.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 digest is not available", exception);
		}
	}
}
