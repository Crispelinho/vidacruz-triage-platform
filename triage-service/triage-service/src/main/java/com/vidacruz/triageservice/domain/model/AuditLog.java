package com.vidacruz.triageservice.domain.model;

import reactor.core.publisher.Mono;

/** Append-only RN-04 audit trail. */
public interface AuditLog {

	Mono<Void> append(AuditEntry entry);
}
