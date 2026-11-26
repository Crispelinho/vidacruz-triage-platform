package com.vidacruz.triageservice.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

public interface AuditEntryRepository extends R2dbcRepository<AuditEntryEntity, UUID> {
}
