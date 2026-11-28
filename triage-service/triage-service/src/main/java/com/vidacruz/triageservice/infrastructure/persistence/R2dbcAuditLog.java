package com.vidacruz.triageservice.infrastructure.persistence;

import org.springframework.stereotype.Repository;

import com.vidacruz.triageservice.domain.model.AuditEntry;
import com.vidacruz.triageservice.domain.model.AuditLog;

import reactor.core.publisher.Mono;

/** R2DBC adapter for the RN-04 audit trail ({@code auditoria_clinica}). */
@Repository
public class R2dbcAuditLog implements AuditLog {

	private final AuditEntryRepository repository;

	public R2dbcAuditLog(AuditEntryRepository repository) {
		this.repository = repository;
	}

	@Override
	public Mono<Void> append(AuditEntry entry) {
		return repository.save(new AuditEntryEntity(entry.id(), entry.patientId(), entry.createdAt(),
				entry.previousStatus(), entry.nextStatus(), entry.eventType())).then();
	}
}
