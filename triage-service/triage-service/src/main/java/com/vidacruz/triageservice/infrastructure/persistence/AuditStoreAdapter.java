package com.vidacruz.triageservice.infrastructure.persistence;

import org.springframework.stereotype.Component;

import com.vidacruz.triageservice.domain.model.AuditEntry;
import com.vidacruz.triageservice.domain.port.AuditStore;

import reactor.core.publisher.Mono;

/** R2DBC implementation of the append-only audit boundary. */
@Component
public class AuditStoreAdapter implements AuditStore {

	private final AuditEntryRepository repository;

	public AuditStoreAdapter(AuditEntryRepository repository) {
		this.repository = repository;
	}

	@Override
	public Mono<Void> append(AuditEntry entry) {
		return repository.save(new AuditEntryEntity(
				entry.id(), entry.patientId(), entry.createdAt(), entry.previousStatus(),
				entry.nextStatus(), entry.eventType())).then();
	}
}
