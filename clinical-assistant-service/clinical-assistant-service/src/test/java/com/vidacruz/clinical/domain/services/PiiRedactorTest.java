package com.vidacruz.clinical.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PiiRedactorTest {

	@Test
	void redactsTheNameAfterAPatientCueAndKeepsClinicalContent() {
		String redacted = PiiRedactor.redact("Paciente Juan Pérez, 54 años, HIV+, dolor torácico");

		assertThat(redacted).isEqualTo("Paciente [NOMBRE], 54 años, HIV+, dolor torácico");
	}

	@ParameterizedTest
	@ValueSource(strings = { "la paciente María de los Ángeles Ruiz", "Sra. Ana Gómez", "doña Rosa",
			"se llama Luis Fernando Díaz", "nombre: Camila Torres", "mi hija Valentina" })
	void redactsNamesAfterCommonCues(String text) {
		assertThat(PiiRedactor.redact(text)).endsWith("[NOMBRE]").doesNotContainPattern("\\p{Lu}\\p{Ll}+$");
	}

	@Test
	void keepsLowercaseWordsAfterTheCue() {
		assertThat(PiiRedactor.redact("paciente con fiebre de 39 grados")).isEqualTo("paciente con fiebre de 39 grados");
	}

	@ParameterizedTest
	@ValueSource(strings = { "CC 1.023.456.789", "C.C. 52123456", "cédula 80123456", "TI 1001234567",
			"documento de identidad: 79876543", "pasaporte AB123456", "historia clínica 445566" })
	void redactsLabelledIdentityDocuments(String document) {
		String redacted = PiiRedactor.redact("paciente con " + document + " refiere cefalea");

		assertThat(redacted).contains("[DOCUMENTO]").endsWith(" refiere cefalea").doesNotContainPattern("[0-9]{4}");
	}

	@ParameterizedTest
	@ValueSource(strings = { "300 123 4567", "3001234567", "+57 310-555-1234", "601 234 5678", "(604) 444 1234" })
	void redactsPhoneNumbers(String phone) {
		assertThat(PiiRedactor.redact("contacto " + phone + " urgente")).isEqualTo("contacto [TELEFONO] urgente");
	}

	@Test
	void redactsEmails() {
		assertThat(PiiRedactor.redact("escribir a juan.perez+eps@correo.com.co hoy"))
				.isEqualTo("escribir a [EMAIL] hoy");
	}

	@Test
	void redactsAnUnlabelledLongDigitRun() {
		assertThat(PiiRedactor.redact("identificado 1023456789 con dolor")).isEqualTo("identificado [DOCUMENTO] con dolor");
	}

	@Test
	void keepsDosesVitalSignsAndDottedUnits() {
		String clinical = "TA 180/110, FC 120, SatO2 88%, adrenalina 1 mg IV cada 3-5 min, penicilina 2.400.000 UI";

		assertThat(PiiRedactor.redact(clinical)).isEqualTo(clinical);
	}

	@Test
	void redactsEveryIdentifierInOneQuestion() {
		String redacted = PiiRedactor.redact(
				"Paciente Juan Pérez CC 79123456, tel 3101234567, juan@mail.com, 54 años con dolor torácico");

		assertThat(redacted).isEqualTo(
				"Paciente [NOMBRE] CC [DOCUMENTO], tel [TELEFONO], [EMAIL], 54 años con dolor torácico");
	}

	@ParameterizedTest
	@ValueSource(strings = { "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?",
			"¿Cuándo se administra el primer antibiótico en sepsis?",
			"¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?" })
	void leavesTheSpecAndDemoQuestionsUnchanged(String question) {
		assertThat(PiiRedactor.redact(question)).isEqualTo(question);
	}

	@Test
	void nullAndEmptyAreReturnedAsIs() {
		assertThat(PiiRedactor.redact(null)).isNull();
		assertThat(PiiRedactor.redact("")).isEmpty();
	}

}
