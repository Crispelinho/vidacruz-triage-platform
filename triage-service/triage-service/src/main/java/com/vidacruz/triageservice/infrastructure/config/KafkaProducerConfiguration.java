package com.vidacruz.triageservice.infrastructure.config;

import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/** Maintained Spring Kafka producer used by the outbox publisher (G-T25). */
@Configuration
public class KafkaProducerConfiguration {

	@Bean
	KafkaTemplate<String, String> kafkaTemplate(KafkaProperties properties) {
		return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(properties.buildProducerProperties()));
	}
}
