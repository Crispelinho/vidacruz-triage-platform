package com.vidacruz.triageservice.infrastructure.config;

import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.vidacruz.triageservice.domain.port.TransactionalExecutor;

import reactor.core.publisher.Mono;

/** Spring transaction implementation of the domain transaction boundary. */
@Component
public class ReactiveTransactionExecutor implements TransactionalExecutor {

	private final TransactionalOperator operator;

	public ReactiveTransactionExecutor(TransactionalOperator operator) {
		this.operator = operator;
	}

	@Override
	public <T> Mono<T> transactional(Mono<T> work) {
		return operator.transactional(work);
	}
}
