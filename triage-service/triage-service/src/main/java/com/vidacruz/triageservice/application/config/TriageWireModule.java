package com.vidacruz.triageservice.application.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.vidacruz.triageservice.domain.dto.LatencyPercentileReport;
import com.vidacruz.triageservice.domain.dto.OperationHourReport;
import com.vidacruz.triageservice.domain.dto.PatientDetailResponse;
import com.vidacruz.triageservice.domain.dto.PatientRegistrationRequest;
import com.vidacruz.triageservice.domain.dto.PatientRegistrationResponse;
import com.vidacruz.triageservice.domain.dto.SlaOperationalReport;
import com.vidacruz.triageservice.event.domain.EventType;
import com.vidacruz.triageservice.event.domain.PacienteAlta;
import com.vidacruz.triageservice.event.domain.PacienteEscalado;
import com.vidacruz.triageservice.event.domain.PacienteEnConsulta;
import com.vidacruz.triageservice.event.domain.PacienteRegistrado;
import com.vidacruz.triageservice.event.domain.TriageEvent;

/**
 * All Jackson wire contracts for triage DTOs and clinical events. Keeping them in a module makes the same JSON
 * available to Spring Boot and to plain unit tests, while the domain packages stay free of framework annotations.
 */
public class TriageWireModule extends SimpleModule {

	public TriageWireModule() {
		setMixInAnnotation(PatientRegistrationRequest.class, PatientRegistrationRequestMixin.class);
		setMixInAnnotation(PatientRegistrationResponse.class, PatientRegistrationResponseMixin.class);
		setMixInAnnotation(PatientDetailResponse.class, PatientDetailResponseMixin.class);
		setMixInAnnotation(OperationHourReport.class, OperationHourReportMixin.class);
		setMixInAnnotation(LatencyPercentileReport.class, LatencyPercentileReportMixin.class);
		setMixInAnnotation(SlaOperationalReport.class, SlaOperationalReportMixin.class);
		setMixInAnnotation(TriageEvent.class, TriageEventMixin.class);
		setMixInAnnotation(PacienteRegistrado.class, PacienteRegistradoMixin.class);
		setMixInAnnotation(PacienteEscalado.class, PacienteEscaladoMixin.class);
		setMixInAnnotation(PacienteEnConsulta.class, PacienteEnConsultaMixin.class);
		setMixInAnnotation(PacienteAlta.class, PacienteAltaMixin.class);
	}

	abstract static class PatientRegistrationRequestMixin {

		@JsonCreator
		PatientRegistrationRequestMixin(
				@JsonProperty("nombre_completo") @JsonAlias("nombreCompleto") String fullName,
				@JsonProperty("edad") Integer age,
				@JsonProperty("frecuencia_cardiaca") @JsonAlias("frecuenciaCardiaca") Integer heartRate,
				@JsonProperty("presion_sistolica") @JsonAlias("presionSistolica") Integer systolic,
				@JsonProperty("presion_diastolica") @JsonAlias("presionDiastolica") Integer diastolic,
				@JsonProperty("spo2") Integer spo2,
				@JsonProperty("temperatura") BigDecimal temperature,
				@JsonProperty("dolor") Integer pain,
				@JsonProperty("gcs") Integer gcs,
				@JsonProperty("queja_principal") @JsonAlias("quejaPrincipal") String complaint) {
		}

		@JsonProperty("nombre_completo") abstract String fullName();

		@JsonProperty("edad") abstract Integer age();

		@JsonProperty("frecuencia_cardiaca") abstract Integer heartRate();

		@JsonProperty("presion_sistolica") abstract Integer systolic();

		@JsonProperty("presion_diastolica") abstract Integer diastolic();

		@JsonProperty("spo2") abstract Integer spo2();

		@JsonProperty("temperatura") abstract BigDecimal temperature();

		@JsonProperty("dolor") abstract Integer pain();

		@JsonProperty("gcs") abstract Integer gcs();

		@JsonProperty("queja_principal") abstract String complaint();
	}

	abstract static class PatientRegistrationResponseMixin {

		@JsonCreator
		PatientRegistrationResponseMixin(
				@JsonProperty("id") UUID id,
				@JsonProperty("nivel") String level,
				@JsonProperty("slaMinutos") int slaMinutes,
				@JsonProperty("estado") String status,
				@JsonProperty("fechaRegistro") Instant registeredAt) {
		}

		@JsonProperty("id") abstract UUID id();

		@JsonProperty("nivel") abstract String level();

		@JsonProperty("slaMinutos") abstract int slaMinutes();

		@JsonProperty("estado") abstract String status();

		@JsonProperty("fechaRegistro") abstract Instant registeredAt();
	}

	abstract static class PatientDetailResponseMixin {

		@JsonCreator
		PatientDetailResponseMixin(
				@JsonProperty("id") UUID id,
				@JsonProperty("nombreCompleto") String fullName,
				@JsonProperty("nivel") String level,
				@JsonProperty("slaMinutos") int slaMinutes,
				@JsonProperty("estado") String status,
				@JsonProperty("escalado") boolean escalated,
				@JsonProperty("fechaRegistro") Instant registeredAt,
				@JsonProperty("tiempoEnEstadoMinutos") long minutesInStatus,
				@JsonProperty("edad") Integer age,
				@JsonProperty("frecuenciaCardiaca") Integer heartRate,
				@JsonProperty("presionSistolica") Integer systolic,
				@JsonProperty("presionDiastolica") Integer diastolic,
				@JsonProperty("spo2") Integer spo2,
				@JsonProperty("temperatura") BigDecimal temperature,
				@JsonProperty("dolor") Integer pain,
				@JsonProperty("gcs") Integer gcs,
				@JsonProperty("quejaPrincipal") String complaint) {
		}

		@JsonProperty("id") abstract UUID id();

		@JsonProperty("nombreCompleto") abstract String fullName();

		@JsonProperty("nivel") abstract String level();

		@JsonProperty("slaMinutos") abstract int slaMinutes();

		@JsonProperty("estado") abstract String status();

		@JsonProperty("escalado") abstract boolean escalated();

		@JsonProperty("fechaRegistro") abstract Instant registeredAt();

		@JsonProperty("tiempoEnEstadoMinutos") abstract long minutesInStatus();

		@JsonProperty("edad") abstract Integer age();

		@JsonProperty("frecuenciaCardiaca") abstract Integer heartRate();

		@JsonProperty("presionSistolica") abstract Integer systolic();

		@JsonProperty("presionDiastolica") abstract Integer diastolic();

		@JsonProperty("spo2") abstract Integer spo2();

		@JsonProperty("temperatura") abstract BigDecimal temperature();

		@JsonProperty("dolor") abstract Integer pain();

		@JsonProperty("gcs") abstract Integer gcs();

		@JsonProperty("quejaPrincipal") abstract String complaint();
	}

	abstract static class OperationHourReportMixin {

		@JsonCreator
		OperationHourReportMixin(
				@JsonProperty("hour") Instant hour,
				@JsonProperty("total") long total,
				@JsonProperty("by_level") Map<String, Long> byLevel) {
		}

		@JsonProperty("hour") abstract Instant hour();

		@JsonProperty("total") abstract long total();

		@JsonProperty("by_level") abstract Map<String, Long> byLevel();
	}

	abstract static class LatencyPercentileReportMixin {

		@JsonCreator
		LatencyPercentileReportMixin(
				@JsonProperty("nivel") String nivel,
				@JsonProperty("p50") double p50Minutes,
				@JsonProperty("p95") double p95Minutes,
				@JsonProperty("p99") double p99Minutes) {
		}

		@JsonProperty("nivel") abstract String nivel();

		@JsonProperty("p50") abstract double p50Minutes();

		@JsonProperty("p95") abstract double p95Minutes();

		@JsonProperty("p99") abstract double p99Minutes();
	}

	abstract static class SlaOperationalReportMixin {

		@JsonCreator
		SlaOperationalReportMixin(
				@JsonProperty("nivel") String nivel,
				@JsonProperty("total") long total,
				@JsonProperty("escalated_count") long escalatedCount,
				@JsonProperty("escalated_pct") double escalatedPct) {
		}

		@JsonProperty("nivel") abstract String nivel();

		@JsonProperty("total") abstract long total();

		@JsonProperty("escalated_count") abstract long escalatedCount();

		@JsonProperty("escalated_pct") abstract double escalatedPct();
	}

	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType", visible = true)
	@JsonSubTypes({
			@JsonSubTypes.Type(value = PacienteRegistrado.class, name = "EVT_01_PACIENTE_REGISTRADO"),
			@JsonSubTypes.Type(value = PacienteEscalado.class, name = "EVT_02_PACIENTE_ESCALADO"),
			@JsonSubTypes.Type(value = PacienteEnConsulta.class, name = "EVT_03_PACIENTE_EN_CONSULTA"),
			@JsonSubTypes.Type(value = PacienteAlta.class, name = "EVT_04_PACIENTE_ALTA")
	})
	abstract static class TriageEventMixin {

		@JsonCreator
		TriageEventMixin(
				@JsonProperty("eventId") UUID eventId,
				@JsonProperty("eventType") EventType eventType,
				@JsonProperty("schemaVersion") int schemaVersion,
				@JsonProperty("pacienteId") UUID pacienteId,
				@JsonProperty("pacienteSequence") long pacienteSequence,
				@JsonProperty("occurredAt") ZonedDateTime occurredAt,
				@JsonProperty("correlationId") String correlationId) {
		}

		@JsonProperty("eventId") abstract UUID getEventId();

		@JsonProperty("eventType") abstract EventType getEventType();

		@JsonProperty("schemaVersion") abstract int getSchemaVersion();

		@JsonProperty("pacienteId") abstract UUID getPacienteId();

		@JsonProperty("pacienteSequence") abstract long getPacienteSequence();

		@JsonProperty("occurredAt") abstract ZonedDateTime getOccurredAt();

		@JsonProperty("correlationId") abstract String getCorrelationId();

		@JsonIgnore abstract Object getPayload();
	}

	abstract static class PacienteRegistradoMixin {

		@JsonCreator
		PacienteRegistradoMixin(
				@JsonProperty("eventId") UUID eventId,
				@JsonProperty("schemaVersion") int schemaVersion,
				@JsonProperty("pacienteId") UUID pacienteId,
				@JsonProperty("pacienteSequence") long pacienteSequence,
				@JsonProperty("occurredAt") ZonedDateTime occurredAt,
				@JsonProperty("correlationId") String correlationId,
				@JsonProperty("nivel") String nivel,
				@JsonProperty("slaMinutos") int slaMinutos,
				@JsonProperty("estado") String estado,
				@JsonProperty("fechaRegistro") ZonedDateTime fechaRegistro) {
		}

		@JsonProperty("nivel") abstract String getNivel();

		@JsonProperty("slaMinutos") abstract int getSlaMinutos();

		@JsonProperty("estado") abstract String getEstado();

		@JsonProperty("fechaRegistro") abstract ZonedDateTime getFechaRegistro();
	}

	abstract static class PacienteEscaladoMixin {

		@JsonCreator
		PacienteEscaladoMixin(
				@JsonProperty("eventId") UUID eventId,
				@JsonProperty("schemaVersion") int schemaVersion,
				@JsonProperty("pacienteId") UUID pacienteId,
				@JsonProperty("pacienteSequence") long pacienteSequence,
				@JsonProperty("occurredAt") ZonedDateTime occurredAt,
				@JsonProperty("correlationId") String correlationId,
				@JsonProperty("nivel") String nivel,
				@JsonProperty("slaMinutos") int slaMinutos,
				@JsonProperty("fechaEscalamiento") ZonedDateTime fechaEscalamiento) {
		}

		@JsonProperty("nivel") abstract String getNivel();

		@JsonProperty("slaMinutos") abstract int getSlaMinutos();

		@JsonProperty("fechaEscalamiento") abstract ZonedDateTime getFechaEscalamiento();
	}

	abstract static class PacienteEnConsultaMixin {

		@JsonCreator
		PacienteEnConsultaMixin(
				@JsonProperty("eventId") UUID eventId,
				@JsonProperty("schemaVersion") int schemaVersion,
				@JsonProperty("pacienteId") UUID pacienteId,
				@JsonProperty("pacienteSequence") long pacienteSequence,
				@JsonProperty("occurredAt") ZonedDateTime occurredAt,
				@JsonProperty("correlationId") String correlationId,
				@JsonProperty("estadoAnterior") String estadoAnterior,
				@JsonProperty("fechaEntradaConsulta") ZonedDateTime fechaEntradaConsulta) {
		}

		@JsonProperty("estadoAnterior") abstract String getEstadoAnterior();

		@JsonProperty("fechaEntradaConsulta") abstract ZonedDateTime getFechaEntradaConsulta();
	}

	abstract static class PacienteAltaMixin {

		@JsonCreator
		PacienteAltaMixin(
				@JsonProperty("eventId") UUID eventId,
				@JsonProperty("schemaVersion") int schemaVersion,
				@JsonProperty("pacienteId") UUID pacienteId,
				@JsonProperty("pacienteSequence") long pacienteSequence,
				@JsonProperty("occurredAt") ZonedDateTime occurredAt,
				@JsonProperty("correlationId") String correlationId,
				@JsonProperty("estadoAnterior") String estadoAnterior,
				@JsonProperty("fechaAlta") ZonedDateTime fechaAlta) {
		}

		@JsonProperty("estadoAnterior") abstract String getEstadoAnterior();

		@JsonProperty("fechaAlta") abstract ZonedDateTime getFechaAlta();
	}
}
