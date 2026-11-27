package com.vidacruz.triageservice.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import com.vidacruz.triageservice.domain.model.MedicalAlert;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** RF-06 alerts stored in PostgreSQL. The caller supplies the transaction; this adapter owns only the SQL. */
@Repository
public class PostgresMedicalAlertRepository implements MedicalAlertRepository {

	static final String INSERT = """
			INSERT INTO medical_alert (id, patient_id, nivel, sla_minutos, fecha_registro,
			                           fecha_escalamiento, minutos_espera, estado, actualizada_en)
			VALUES (:id, :patientId, :level, :slaMinutes, :registeredAt,
			        :escalatedAt, :waitMinutes, 'PENDIENTE', :updatedAt)
			ON CONFLICT (patient_id) DO NOTHING
			""";

	static final String SELECT_ACTIVE = """
			SELECT %s
			  FROM medical_alert
			 WHERE estado IN ('PENDIENTE', 'RECONOCIDA')
			 ORDER BY CASE nivel
			              WHEN 'CRITICO' THEN 0 WHEN 'URGENTE' THEN 1 WHEN 'URGENTE_2' THEN 2
			              WHEN 'NO_URGENTE' THEN 3 ELSE 4
			          END, fecha_escalamiento, id
			""".formatted(MedicalAlertRowMapper.COLUMNS);

	static final String SELECT_BY_ID = """
			SELECT %s
			  FROM medical_alert
			 WHERE id = :id
			""".formatted(MedicalAlertRowMapper.COLUMNS);

	static final String ACKNOWLEDGE = """
			UPDATE medical_alert
			   SET estado = 'RECONOCIDA', responsable = :responsible,
			       reconocida_en = :at, actualizada_en = :at
			 WHERE id = :id AND estado = 'PENDIENTE'
			""";

	static final String RESOLVE = """
			UPDATE medical_alert
			   SET estado = 'RESUELTA', resuelta_en = :at, actualizada_en = :at
			 WHERE patient_id = :patientId AND estado IN ('PENDIENTE', 'RECONOCIDA')
			""";

	private final DatabaseClient client;

	public PostgresMedicalAlertRepository(DatabaseClient client) {
		this.client = client;
	}

	@Override
	public Mono<Void> create(MedicalAlert alert) {
		return client.sql(INSERT)
				.bind("id", alert.id())
				.bind("patientId", alert.patientId())
				.bind("level", alert.level().name())
				.bind("slaMinutes", alert.slaMinutes())
				.bind("registeredAt", alert.registeredAt())
				.bind("escalatedAt", alert.escalatedAt())
				.bind("waitMinutes", alert.waitMinutes())
				.bind("updatedAt", alert.updatedAt())
				.fetch()
				.rowsUpdated()
				.then();
	}

	@Override
	public Flux<MedicalAlert> findActive() {
		return client.sql(SELECT_ACTIVE)
				.map(MedicalAlertRowMapper::map)
				.all();
	}

	@Override
	public Mono<MedicalAlert> findById(UUID id) {
		return client.sql(SELECT_BY_ID)
				.bind("id", id)
				.map(MedicalAlertRowMapper::map)
				.first();
	}

	@Override
	public Mono<Boolean> acknowledge(UUID id, String responsible, Instant at) {
		return client.sql(ACKNOWLEDGE)
				.bind("id", id)
				.bind("responsible", responsible)
				.bind("at", at)
				.fetch()
				.rowsUpdated()
				.map(updated -> updated > 0);
	}

	@Override
	public Mono<Boolean> resolveForPatient(UUID patientId, Instant at) {
		return client.sql(RESOLVE)
				.bind("patientId", patientId)
				.bind("at", at)
				.fetch()
				.rowsUpdated()
				.map(updated -> updated > 0);
	}
}
