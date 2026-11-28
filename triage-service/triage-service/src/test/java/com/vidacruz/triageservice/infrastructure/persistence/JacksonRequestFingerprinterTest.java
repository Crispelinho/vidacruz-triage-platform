package com.vidacruz.triageservice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import com.vidacruz.triageservice.application.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.domain.model.PatientRegistration;

class JacksonRequestFingerprinterTest {

	private final JacksonRequestFingerprinter fingerprinter = new JacksonRequestFingerprinter();

	@Test
	void digestIsSha256OfTheCanonicalSortedJson() throws Exception {
		// Pinned canonical form (DEC-15): changing it would make stored keys reject their own retries.
		String canonical = "{\"dolor\":8,\"edad\":40,\"frecuencia_cardiaca\":130,\"gcs\":15,"
				+ "\"nombre_completo\":\"Paciente de prueba\",\"presion_diastolica\":110,\"presion_sistolica\":180,"
				+ "\"queja_principal\":\"Control\",\"spo2\":97,\"temperatura\":36.8}";
		String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(canonical.getBytes(StandardCharsets.UTF_8)));

		assertThat(fingerprinter.fingerprint(registration("Paciente de prueba", "Control"))).isEqualTo(expected);
	}

	@Test
	void surroundingWhitespaceDoesNotChangeTheDigest() {
		assertThat(fingerprinter.fingerprint(registration("  Paciente de prueba ", " Control  ")))
				.isEqualTo(fingerprinter.fingerprint(registration("Paciente de prueba", "Control")));
	}

	private static PatientRegistration registration(String name, String complaint) {
		return new PatientRegistrationRequest(name, 40, 130, 180, 110, 97, BigDecimal.valueOf(36.8), 8, 15, complaint)
				.toDomain();
	}
}
