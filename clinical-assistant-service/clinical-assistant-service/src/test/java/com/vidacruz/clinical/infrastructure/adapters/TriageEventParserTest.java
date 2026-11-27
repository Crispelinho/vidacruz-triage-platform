package com.vidacruz.clinical.infrastructure.adapters;

import com.vidacruz.clinical.domain.model.urgency.PatientStatus;
import com.vidacruz.clinical.domain.model.urgency.TriageLevel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TriageEventParserTest {

    private static final String PATIENT = "11111111-1111-4111-8111-111111111111";

    @Test
    void parsesRegistrationWithLevelAndEnvelope() {
        var snapshot = TriageEventParser.parse("""
                {"eventType":"EVT_01_PACIENTE_REGISTRADO","eventId":"22222222-2222-4222-8222-222222222222",
                 "schemaVersion":1,"pacienteId":"%s","pacienteSequence":1,
                 "occurredAt":"2026-11-26T10:00:00Z","correlationId":"33333333-3333-4333-8333-333333333333",
                 "nivel":"CRITICO","slaMinutos":5,"estado":"REGISTRADO"}
                """.formatted(PATIENT), PATIENT).orElseThrow();

        assertThat(snapshot.patientId()).isEqualTo(UUID.fromString(PATIENT));
        assertThat(snapshot.level()).contains(TriageLevel.CRITICAL);
        assertThat(snapshot.status()).isEqualTo(PatientStatus.REGISTRADO);
        assertThat(snapshot.occurredAt()).isEqualTo(java.time.Instant.parse("2026-11-26T10:00:00Z"));
        assertThat(snapshot.sequence()).isEqualTo(1);
        assertThat(snapshot.isActive()).isTrue();
    }

    @Test
    void keepsStoredLevelForConsultationAndDischargeUsingTheRecordKey() {
        var consultation = TriageEventParser.parse("""
                {"eventType":"EVT_03_PACIENTE_EN_CONSULTA","pacienteId":"%s","pacienteSequence":2,
                 "occurredAt":"2026-11-26T10:05:00Z","estadoAnterior":"REGISTRADO"}
                """.formatted(PATIENT), PATIENT).orElseThrow();

        assertThat(consultation.level()).isEmpty();
        assertThat(consultation.status()).isEqualTo(PatientStatus.EN_CONSULTA);
        assertThat(consultation.isActive()).isTrue();

        var discharge = TriageEventParser.parse("""
                {"eventType":"EVT_04_PACIENTE_ALTA","pacienteSequence":3,
                 "occurredAt":"2026-11-26T10:30:00Z","estadoAnterior":"EN_CONSULTA"}
                """, PATIENT).orElseThrow();

        assertThat(discharge.status()).isEqualTo(PatientStatus.ALTA);
        assertThat(discharge.isActive()).isFalse();
    }

    @Test
    void parsesTheWireNamesPublishedByTriage() {
        var registration = TriageEventParser.parse("""
                {"eventType":"paciente_registrado","pacienteId":"%s","pacienteSequence":1,
                 "occurredAt":"2026-11-26T10:00:00Z","nivel":"CRITICO"}
                """.formatted(PATIENT), PATIENT).orElseThrow();

        assertThat(registration.status()).isEqualTo(PatientStatus.REGISTRADO);
        assertThat(registration.level()).contains(TriageLevel.CRITICAL);

        var discharge = TriageEventParser.parse("""
                {"eventType":"paciente_alta","pacienteSequence":2,
                 "occurredAt":"2026-11-26T10:30:00Z"}
                """, PATIENT).orElseThrow();

        assertThat(discharge.status()).isEqualTo(PatientStatus.ALTA);
    }

    @Test
    void skipsUnknownEventTypes() {
        assertThat(TriageEventParser.parse("""
                {"eventType":"OTRO_EVENTO","pacienteId":"%s","pacienteSequence":1}
                """.formatted(PATIENT), PATIENT)).isEmpty();
    }

    @Test
    void rejectsMissingSequence() {
        assertThatThrownBy(() -> TriageEventParser.parse("""
                {"eventType":"EVT_02_PACIENTE_ESCALADO","pacienteId":"%s","nivel":"URGENTE",
                 "occurredAt":"2026-11-26T10:05:00Z"}
                """.formatted(PATIENT), PATIENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pacienteSequence");
    }
}
