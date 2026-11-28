package com.vidacruz.triageservice.infrastructure.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.vidacruz.triageservice.domain.model.TransactionRunner;

import reactor.core.publisher.Mono;

/** {@link TransactionRunner} backed by the reactive R2DBC transaction manager. */
@Component
public class R2dbcTransactionRunner implements TransactionRunner {

	private final TransactionalOperator transactionalOperator;

	public R2dbcTransactionRunner(TransactionalOperator transactionalOperator) {
		this.transactionalOperator = transactionalOperator;
	}

	@Override
	public <T> Mono<T> inTransaction(Mono<T> work) {
		return transactionalOperator.transactional(work);
	}
}
