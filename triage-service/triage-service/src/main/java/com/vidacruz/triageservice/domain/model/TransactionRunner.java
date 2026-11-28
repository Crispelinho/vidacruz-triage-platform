package com.vidacruz.triageservice.domain.model;

import reactor.core.publisher.Mono;

/** Runs a unit of work in one database transaction: every write inside commits or rolls back together. */
public interface TransactionRunner {

	<T> Mono<T> inTransaction(Mono<T> work);
}
