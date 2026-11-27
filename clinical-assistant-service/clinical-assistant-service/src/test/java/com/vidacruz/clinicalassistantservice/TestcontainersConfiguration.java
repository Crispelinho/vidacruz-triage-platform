package com.vidacruz.clinicalassistantservice;

import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Configuration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgresContainer() {
		// pgvector image: the Spring AI PgVector store needs the vector extension
		return new PostgreSQLContainer<>(
				DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
	}

	// @ServiceConnection only exposes R2DBC details; the explicit DataSource needs JDBC details
	@Bean
	JdbcConnectionDetails jdbcConnectionDetails(PostgreSQLContainer<?> postgresContainer) {
		return new JdbcConnectionDetails() {
			@Override
			public String getJdbcUrl() {
				return postgresContainer.getJdbcUrl();
			}

			@Override
			public String getUsername() {
				return postgresContainer.getUsername();
			}

			@Override
			public String getPassword() {
				return postgresContainer.getPassword();
			}
		};
	}

	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {
		return new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
	}

}
