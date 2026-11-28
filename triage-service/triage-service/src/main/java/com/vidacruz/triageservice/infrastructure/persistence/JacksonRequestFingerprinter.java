package com.vidacruz.triageservice.infrastructure.persistence;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.domain.model.PatientRegistration;
import com.vidacruz.triageservice.domain.model.RequestFingerprinter;

/**
 * SHA-256 of the canonical, validated request body: sorted wire keys and normalized values (DEC-15, ADR-12). A plain
 * {@link ObjectMapper} is used on purpose, so the digest never depends on the Jackson settings of the application.
 */
@Component
public class JacksonRequestFingerprinter implements RequestFingerprinter {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public String fingerprint(PatientRegistration registration) {
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
}
