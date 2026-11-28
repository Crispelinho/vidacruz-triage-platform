package com.vidacruz.triageservice.domain.port;

import reactor.core.publisher.Mono;

/** Transaction boundary with the same semantics as the reactive Spring transaction operator. */
public interface TransactionalExecutor {

	<T> Mono<T> transactional(Mono<T> work);
}
