package com.vidacruz.triageservice.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

public interface PatientRepository extends R2dbcRepository<PatientEntity, UUID> {
}
