package com.vidacruz.triageservice.domain.port;

import com.vidacruz.triageservice.domain.model.AuditEntry;

import reactor.core.publisher.Mono;

/** Append-only audit boundary for RN-04. */
public interface AuditStore {

	Mono<Void> append(AuditEntry entry);
}
