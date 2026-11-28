package com.vidacruz.triageservice.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the triage JSON contracts with Spring Boot's ObjectMapper. */
@Configuration
public class JacksonConfiguration {

	@Bean
	public TriageWireModule triageWireModule() {
		return new TriageWireModule();
	}
}
