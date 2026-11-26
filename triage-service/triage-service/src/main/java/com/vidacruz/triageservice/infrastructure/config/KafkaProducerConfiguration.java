package com.vidacruz.triageservice.infrastructure.config;

import java.util.Map;

import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

/** Reactive Kafka sender used by the outbox publisher. */
@Configuration
public class KafkaProducerConfiguration {

	@Bean
	KafkaSender<String, String> kafkaSender(KafkaProperties properties) {
		Map<String, Object> producerProperties = properties.buildProducerProperties();
		return KafkaSender.create(SenderOptions.create(producerProperties));
	}
}
