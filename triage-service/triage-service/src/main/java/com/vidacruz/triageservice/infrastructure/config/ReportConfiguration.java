package com.vidacruz.triageservice.infrastructure.config;

import com.vidacruz.triageservice.domain.report.ReportPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the RF-05 policy from configuration so it can be changed without a rebuild. */
@Configuration
public class ReportConfiguration {

	@Bean
	public ReportPolicy reportPolicy(ReportProperties properties) {
		return new ReportPolicy(properties.maxRange());
	}
}
