package com.vidacruz.triageservice.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PatientRepository extends R2dbcRepository<PatientEntity, UUID> {

	@Query("SELECT id, nombre, edad, frecuencia_cardiaca, presion_sistolica, presion_diastolica, spo2, " +
			"temperatura, dolor, gcs, queja_principal, nivel, sla_minutos, estado, escalado, " +
			"fecha_registro, ultimo_cambio_en, estado_desde FROM paciente " +
			"WHERE estado = 'REGISTRADO' AND escalado = FALSE")
	Flux<PatientEntity> findEscalationCandidates();

	@Modifying
	@Query("UPDATE paciente SET estado = :nextStatus, ultimo_cambio_en = :changedAt, " +
			"estado_desde = :changedAt WHERE id = :id AND estado = :expectedStatus")
	Mono<Integer> changeStatus(@Param("id") UUID id,
			@Param("expectedStatus") String expectedStatus,
			@Param("nextStatus") String nextStatus,
			@Param("changedAt") Instant changedAt);

	@Modifying
	@Query("UPDATE paciente SET escalado = TRUE, ultimo_cambio_en = :changedAt " +
			"WHERE id = :id AND escalado = FALSE AND estado = 'REGISTRADO'")
	Mono<Integer> escalate(@Param("id") UUID id, @Param("changedAt") Instant changedAt);
}
