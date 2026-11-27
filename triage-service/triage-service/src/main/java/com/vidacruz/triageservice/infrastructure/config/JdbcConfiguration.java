package com.vidacruz.triageservice.infrastructure.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Boot skips JDBC DataSource auto-configuration when an R2DBC ConnectionFactory is present. Flyway needs JDBC,
 * so a minimal DataSource is declared explicitly here and runs the migrations before requests are accepted.
 * DriverManagerDataSource is used because this module has spring-jdbc but no connection pool on its classpath.
 */
// Only the local overlay/deployment sets spring.datasource.url. Without that guard, tests and plain JVM runs get a
// DataSource with no URL and the actuator reports DOWN.
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@Configuration(proxyBeanMethods = false)
class JdbcConfiguration {

	@Bean
	@ConfigurationProperties("spring.datasource")
	DataSourceProperties dataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	DataSource dataSource(DataSourceProperties properties) {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.postgresql.Driver");
		dataSource.setUrl(properties.getUrl());
		dataSource.setUsername(properties.getUsername());
		dataSource.setPassword(properties.getPassword());
		return dataSource;
	}

}
