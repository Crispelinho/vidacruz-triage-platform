package com.vidacruz.clinical_assistant_service.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boot skips JDBC DataSource auto-configuration when an R2DBC ConnectionFactory is present.
 * PGVectorStore (Spring AI) needs JDBC, so the DataSource is declared explicitly here.
 */
@Configuration(proxyBeanMethods = false)
class JdbcConfiguration {

	@Bean
	@ConfigurationProperties("spring.datasource")
	DataSourceProperties dataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	DataSource dataSource(DataSourceProperties properties, ObjectProvider<JdbcConnectionDetails> connectionDetails) {
		// Test containers expose their coordinates through JdbcConnectionDetails; production uses spring.datasource.*
		JdbcConnectionDetails details = connectionDetails.getIfAvailable(() -> fromProperties(properties));
		return DataSourceBuilder.create()
				.url(details.getJdbcUrl())
				.username(details.getUsername())
				.password(details.getPassword())
				.build();
	}

	private static JdbcConnectionDetails fromProperties(DataSourceProperties properties) {
		return new JdbcConnectionDetails() {
			@Override
			public String getJdbcUrl() {
				return properties.getUrl();
			}

			@Override
			public String getUsername() {
				return properties.getUsername();
			}

			@Override
			public String getPassword() {
				return properties.getPassword();
			}
		};
	}

}
