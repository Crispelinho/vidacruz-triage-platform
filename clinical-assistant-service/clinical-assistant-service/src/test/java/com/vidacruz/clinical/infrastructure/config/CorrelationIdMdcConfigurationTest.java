package com.vidacruz.clinical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.vidacruz.clinical.application.filter.CorrelationId;

import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class CorrelationIdMdcConfigurationTest {

	@BeforeEach
	void enablePropagation() {
		CorrelationIdMdcConfiguration.register();
		Hooks.enableAutomaticContextPropagation();
	}

	/** The hook stays on: it is global per JVM and production enables it once (context-propagation: auto). */
	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void exposesTheReactorCorrelationIdToTheMdcAcrossThreads() {
		AtomicReference<String> seen = new AtomicReference<>();

		Mono.just("work")
				.publishOn(Schedulers.parallel())
				.doOnNext(value -> seen.set(MDC.get(CorrelationIdMdcConfiguration.MDC_KEY)))
				.contextWrite(context -> context.put(CorrelationId.CONTEXT_KEY, "corr-123"))
				.block();

		assertThat(seen).hasValue("corr-123");
	}

	@Test
	void leavesTheMdcEmptyOutsideARequest() {
		AtomicReference<String> seen = new AtomicReference<>("unset");

		Mono.just("work")
				.publishOn(Schedulers.parallel())
				.doOnNext(value -> seen.set(MDC.get(CorrelationIdMdcConfiguration.MDC_KEY)))
				.block();

		assertThat(seen.get()).isNull();
	}
}
