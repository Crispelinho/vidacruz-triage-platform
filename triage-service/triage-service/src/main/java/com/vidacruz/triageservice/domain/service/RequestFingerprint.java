package com.vidacruz.triageservice.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import com.vidacruz.triageservice.domain.model.PatientRegistration;

/** SHA-256 of the canonical, validated request body: sorted wire keys and normalized values (DEC-15, ADR-12). */
public final class RequestFingerprint {

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
		StringBuilder json = new StringBuilder("{");
		boolean first = true;
		for (Map.Entry<String, Object> entry : canonical.entrySet()) {
			if (!first) {
				json.append(',');
			}
			first = false;
			appendString(json, entry.getKey());
			json.append(':');
			appendValue(json, entry.getValue());
		}
		json.append('}');
		return of(json.toString());
	}

	public static String of(String canonicalJson) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(canonicalJson.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 digest is not available", exception);
		}
	}

	private static void appendValue(StringBuilder json, Object value) {
		if (value == null) {
			json.append("null");
		} else if (value instanceof String text) {
			appendString(json, text);
		} else if (value instanceof Number number) {
			json.append(number);
		} else {
			throw new IllegalStateException("Unsupported canonical value: " + value.getClass().getName());
		}
	}

	private static void appendString(StringBuilder json, String value) {
		json.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"' -> json.append("\\\"");
				case '\\' -> json.append("\\\\");
				case '\b' -> json.append("\\b");
				case '\f' -> json.append("\\f");
				case '\n' -> json.append("\\n");
				case '\r' -> json.append("\\r");
				case '\t' -> json.append("\\t");
				default -> {
					if (c < 0x20) {
						json.append(String.format("\\u%04x", (int) c));
					} else {
						json.append(c);
					}
				}
			}
		}
		json.append('"');
	}
}
