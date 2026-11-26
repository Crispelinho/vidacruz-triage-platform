package com.vidacruz.triageservice.event;

import com.vidacruz.triageservice.event.domain.EventType;
import com.vidacruz.triageservice.event.domain.PacienteAlta;
import com.vidacruz.triageservice.event.domain.PacienteEscalado;
import com.vidacruz.triageservice.event.domain.PacienteEnConsulta;
import com.vidacruz.triageservice.event.domain.PacienteRegistrado;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventEnvelopeValidationTest {

    private static final UUID PACIENTE_ID = UUID.randomUUID();
    private static final String CORRELATION_ID = "correlation-" + UUID.randomUUID();
    private static final ZonedDateTime NOW = ZonedDateTime.now();

    @Test
    void testPacienteRegistradoPayloadStructure() {
        UUID eventId = UUID.randomUUID();
        PacienteRegistrado event = new PacienteRegistrado(
                eventId, 1, PACIENTE_ID, 1L, NOW, CORRELATION_ID,
                "VERDE", 60, "REGISTRADO", NOW
        );

        assertEquals(EventType.EVT_01_PACIENTE_REGISTRADO, event.getEventType());
        assertEquals("VERDE", event.getNivel());
        assertEquals(60, event.getSlaMinutos());
        assertEquals("REGISTRADO", event.getEstado());
        assertEquals(NOW, event.getFechaRegistro());
        assertNotNull(event.getPayload());
    }

    @Test
    void testPacienteEscaladoPayloadStructure() {
        UUID eventId = UUID.randomUUID();
        PacienteEscalado event = new PacienteEscalado(
                eventId, 1, PACIENTE_ID, 2L, NOW, CORRELATION_ID,
                "AMARILLO", 30, NOW
        );

        assertEquals(EventType.EVT_02_PACIENTE_ESCALADO, event.getEventType());
        assertEquals("AMARILLO", event.getNivel());
        assertEquals(30, event.getSlaMinutos());
        assertEquals(NOW, event.getFechaEscalamiento());
        assertNotNull(event.getPayload());
    }

    @Test
    void testPacienteEnConsultaPayloadStructure() {
        UUID eventId = UUID.randomUUID();
        PacienteEnConsulta event = new PacienteEnConsulta(
                eventId, 1, PACIENTE_ID, 3L, NOW, CORRELATION_ID,
                "VERDE", NOW
        );

        assertEquals(EventType.EVT_03_PACIENTE_EN_CONSULTA, event.getEventType());
        assertEquals("VERDE", event.getEstadoAnterior());
        assertEquals(NOW, event.getFechaEntradaConsulta());
        assertNotNull(event.getPayload());
    }

    @Test
    void testPacienteAltaPayloadStructure() {
        UUID eventId = UUID.randomUUID();
        PacienteAlta event = new PacienteAlta(
                eventId, 1, PACIENTE_ID, 4L, NOW, CORRELATION_ID,
                "AMARILLO", NOW
        );

        assertEquals(EventType.EVT_04_PACIENTE_ALTA, event.getEventType());
        assertEquals("AMARILLO", event.getEstadoAnterior());
        assertEquals(NOW, event.getFechaAlta());
        assertNotNull(event.getPayload());
    }

    @Test
    void testUUIDParsing() {
        String uuidString = "12345678-1234-1234-1234-123456789012";
        UUID parsedUuid = UUID.fromString(uuidString);
        assertEquals(uuidString, parsedUuid.toString());
    }

    @Test
    void testPacienteIdAsUUID() {
        UUID eventId = UUID.randomUUID();
        UUID testPacienteId = UUID.fromString("87654321-4321-4321-4321-210987654321");

        PacienteRegistrado event = new PacienteRegistrado(
                eventId, 1, testPacienteId, 1L, NOW, CORRELATION_ID,
                "VERDE", 60, "REGISTRADO", NOW
        );

        assertEquals(testPacienteId, event.getPacienteId());
        assertEquals("87654321-4321-4321-4321-210987654321", event.getPacienteId().toString());
    }

    @Test
    void testEventIdAsUUID() {
        UUID eventId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        PacienteRegistrado event = new PacienteRegistrado(
                eventId, 1, PACIENTE_ID, 1L, NOW, CORRELATION_ID,
                "VERDE", 60, "REGISTRADO", NOW
        );

        assertEquals(eventId, event.getEventId());
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", event.getEventId().toString());
    }

    @Test
    void testNullEventIdThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new PacienteRegistrado(
                        null, 1, PACIENTE_ID, 1L, NOW, CORRELATION_ID,
                        "VERDE", 60, "REGISTRADO", NOW
                )
        );
    }

    @Test
    void testNullPacienteIdThrows() {
        UUID eventId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                new PacienteRegistrado(
                        eventId, 1, null, 1L, NOW, CORRELATION_ID,
                        "VERDE", 60, "REGISTRADO", NOW
                )
        );
    }

    @Test
    void testNullCorrelationIdThrows() {
        UUID eventId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                new PacienteRegistrado(
                        eventId, 1, PACIENTE_ID, 1L, NOW, null,
                        "VERDE", 60, "REGISTRADO", NOW
                )
        );
    }

    @Test
    void testBlankCorrelationIdThrows() {
        UUID eventId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                new PacienteRegistrado(
                        eventId, 1, PACIENTE_ID, 1L, NOW, "   ",
                        "VERDE", 60, "REGISTRADO", NOW
                )
        );
    }

    @Test
    void testPacienteSequencePreserved() {
        UUID eventId = UUID.randomUUID();
        long sequence = 42L;

        PacienteRegistrado event = new PacienteRegistrado(
                eventId, 1, PACIENTE_ID, sequence, NOW, CORRELATION_ID,
                "VERDE", 60, "REGISTRADO", NOW
        );

        assertEquals(sequence, event.getPacienteSequence());
    }

    @Test
    void testSchemaVersionPreserved() {
        UUID eventId = UUID.randomUUID();
        int schemaVersion = 1;

        PacienteRegistrado event = new PacienteRegistrado(
                eventId, schemaVersion, PACIENTE_ID, 1L, NOW, CORRELATION_ID,
                "VERDE", 60, "REGISTRADO", NOW
        );

        assertEquals(schemaVersion, event.getSchemaVersion());
    }

    @Test
    void testOccurredAtPreserved() {
        UUID eventId = UUID.randomUUID();
        ZonedDateTime specificTime = ZonedDateTime.parse("2026-11-26T15:30:00+00:00");

        PacienteRegistrado event = new PacienteRegistrado(
                eventId, 1, PACIENTE_ID, 1L, specificTime, CORRELATION_ID,
                "VERDE", 60, "REGISTRADO", NOW
        );

        assertEquals(specificTime, event.getOccurredAt());
    }
}
