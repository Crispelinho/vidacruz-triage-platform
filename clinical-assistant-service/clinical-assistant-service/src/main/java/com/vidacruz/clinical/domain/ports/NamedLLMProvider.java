package com.vidacruz.clinical.domain.ports;

/** An {@link LLMProvider} that can identify itself in the configured failover chain and in telemetry. */
public interface NamedLLMProvider extends LLMProvider {

	String key();

	String model();

}
