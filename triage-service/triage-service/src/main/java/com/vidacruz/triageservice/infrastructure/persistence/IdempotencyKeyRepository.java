package com.vidacruz.triageservice.infrastructure.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface IdempotencyKeyRepository
		extends ReactiveCrudRepository<IdempotencyKeyEntity, String>, IdempotencyKeyOperations {
}
