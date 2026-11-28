package com.vidacruz.triageservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.event.infrastructure.contract.EventType;
import com.vidacruz.triageservice.event.infrastructure.contract.PacienteAlta;
import com.vidacruz.triageservice.event.infrastructure.contract.PacienteEscalado;
import com.vidacruz.triageservice.event.infrastructure.contract.PacienteEnConsulta;
import com.vidacruz.triageservice.event.infrastructure.contract.PacienteRegistrado;
import com.vidacruz.triageservice.event.infrastructure.contract.TriageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TriageEventSerializationTest {

    private ObjectMapper objectMapper;
    private UUID pacienteId;
    private String correlationId;
    private ZonedDateTime now;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        pacienteId = UUID.randomUUID();
        correlationId = UUID.randomUUID().toString();
        now = ZonedDateTime.now();
    }

    @Test
    void testPacienteRegistradoJsonRoundTrip() throws Exception {
        UUID eventId = UUID.randomUUID();
        PacienteRegistrado original = new PacienteRegistrado(
                eventId, 1, pacienteId, 1L, now, correlationId,
                "VERDE", 60, "REGISTRADO", now
        );

        String json = objectMapper.writeValueAsString(original);
        TriageEvent deserialized = objectMapper.readValue(json, TriageEvent.class);

        assertInstanceOf(PacienteRegistrado.class, deserialized);
        PacienteRegistrado result = (PacienteRegistrado) deserialized;
        assertEquals(eventId, result.getEventId());
        assertEquals(EventType.EVT_01_PACIENTE_REGISTRADO, result.getEventType());
        assertEquals(1, result.getSchemaVersion());
        assertEquals(pacienteId, result.getPacienteId());
        assertEquals(1L, result.getPacienteSequence());
        assertEquals("VERDE", result.getNivel());
        assertEquals(60, result.getSlaMinutos());
        assertEquals("REGISTRADO", result.getEstado());
    }

    @Test
    void testPacienteEscaladoJsonRoundTrip() throws Exception {
        UUID eventId = UUID.randomUUID();
        PacienteEscalado original = new PacienteEscalado(
                eventId, 1, pacienteId, 2L, now, correlationId,
                "AMARILLO", 30, now
        );

        String json = objectMapper.writeValueAsString(original);
        TriageEvent deserialized = objectMapper.readValue(json, TriageEvent.class);

        assertInstanceOf(PacienteEscalado.class, deserialized);
        PacienteEscalado result = (PacienteEscalado) deserialized;
        assertEquals(eventId, result.getEventId());
        assertEquals(EventType.EVT_02_PACIENTE_ESCALADO, result.getEventType());
        assertEquals("AMARILLO", result.getNivel());
    }

    @Test
    void testPacienteEnConsultaJsonRoundTrip() throws Exception {
        UUID eventId = UUID.randomUUID();
        PacienteEnConsulta original = new PacienteEnConsulta(
                eventId, 1, pacienteId, 3L, now, correlationId,
                "VERDE", now
        );

        String json = objectMapper.writeValueAsString(original);
        TriageEvent deserialized = objectMapper.readValue(json, TriageEvent.class);

        assertInstanceOf(PacienteEnConsulta.class, deserialized);
        PacienteEnConsulta result = (PacienteEnConsulta) deserialized;
        assertEquals(eventId, result.getEventId());
        assertEquals(EventType.EVT_03_PACIENTE_EN_CONSULTA, result.getEventType());
        assertEquals("VERDE", result.getEstadoAnterior());
    }

    @Test
    void testPacienteAltaJsonRoundTrip() throws Exception {
        UUID eventId = UUID.randomUUID();
        PacienteAlta original = new PacienteAlta(
                eventId, 1, pacienteId, 4L, now, correlationId,
                "AMARILLO", now
        );

        String json = objectMapper.writeValueAsString(original);
        TriageEvent deserialized = objectMapper.readValue(json, TriageEvent.class);

        assertInstanceOf(PacienteAlta.class, deserialized);
        PacienteAlta result = (PacienteAlta) deserialized;
        assertEquals(eventId, result.getEventId());
        assertEquals(EventType.EVT_04_PACIENTE_ALTA, result.getEventType());
        assertEquals("AMARILLO", result.getEstadoAnterior());
    }

    @Test
    void testSchemaVersionIsInteger() throws Exception {
        UUID eventId = UUID.randomUUID();
        PacienteRegistrado event = new PacienteRegistrado(
                eventId, 1, pacienteId, 1L, now, correlationId,
                "VERDE", 60, "REGISTRADO", now
        );

        String json = objectMapper.writeValueAsString(event);

        assertTrue(json.contains("\"schemaVersion\":1"));

        TriageEvent deserialized = objectMapper.readValue(json, TriageEvent.class);
        assertEquals(1, deserialized.getSchemaVersion());
    }

    @Test
    void testMissingEventIdRejected() throws Exception {
        String jsonMissingEventId = """
                {
                    "eventType": "EVT_01_PACIENTE_REGISTRADO",
                    "schemaVersion": 1,
                    "pacienteId": "12345678-1234-1234-1234-123456789012",
                    "pacienteSequence": 1,
                    "occurredAt": "2026-11-26T10:00:00+00:00",
                    "correlationId": "corr-123",
                    "nivel": "VERDE",
                    "slaMinutos": 60,
                    "estado": "REGISTRADO",
                    "fechaRegistro": "2026-11-26T10:00:00+00:00"
                }
                """;

        assertThrows(Exception.class, () ->
                objectMapper.readValue(jsonMissingEventId, TriageEvent.class)
        );
    }

    @Test
    void testMissingPacienteIdRejected() throws Exception {
        String jsonMissingPacienteId = """
                {
                    "eventId": "12345678-1234-1234-1234-123456789012",
                    "eventType": "EVT_01_PACIENTE_REGISTRADO",
                    "schemaVersion": 1,
                    "pacienteSequence": 1,
                    "occurredAt": "2026-11-26T10:00:00+00:00",
                    "correlationId": "corr-123",
                    "nivel": "VERDE",
                    "slaMinutos": 60,
                    "estado": "REGISTRADO",
                    "fechaRegistro": "2026-11-26T10:00:00+00:00"
                }
                """;

        assertThrows(Exception.class, () ->
                objectMapper.readValue(jsonMissingPacienteId, TriageEvent.class)
        );
    }

    @Test
    void testPreservesEventIdUponDeserialization() throws Exception {
        UUID eventId = UUID.fromString("12345678-1234-1234-1234-123456789012");
        String json = """
                {
                    "eventId": "12345678-1234-1234-1234-123456789012",
                    "eventType": "EVT_01_PACIENTE_REGISTRADO",
                    "schemaVersion": 1,
                    "pacienteId": "87654321-4321-4321-4321-210987654321",
                    "pacienteSequence": 1,
                    "occurredAt": "2026-11-26T10:00:00+00:00",
                    "correlationId": "corr-123",
                    "nivel": "VERDE",
                    "slaMinutos": 60,
                    "estado": "REGISTRADO",
                    "fechaRegistro": "2026-11-26T10:00:00+00:00"
                }
                """;

        TriageEvent event = objectMapper.readValue(json, TriageEvent.class);
        assertEquals(eventId, event.getEventId());
    }
}
