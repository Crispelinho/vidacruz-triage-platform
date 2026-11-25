package com.vidacruz.clinicalassistantservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ClinicalAssistantServiceApplicationTests {

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private DatabaseClient databaseClient;

	@Autowired
	private JdbcConnectionDetails jdbcConnectionDetails;

	@Autowired
	private PostgreSQLContainer<?> postgresContainer;

	// The PgVector store requires an EmbeddingModel bean; no provider is configured in the scaffold
	@MockitoBean
	EmbeddingModel embeddingModel;

	@Test
	void contextLoads() {
	}

	@Test
	void actuatorHealthReportsUp() {
		webTestClient.get().uri("/actuator/health")
				.exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.status").isEqualTo("UP");
	}

	@Test
	void unknownBusinessRouteRequiresAuthentication() {
		webTestClient.get().uri("/asistente/estado-urgencias")
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void jdbcDataSourceSelectOneRoundTrip() {
		assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
	}

	@Test
	void jdbcConnectionDetailsUsePostgresTestcontainer() {
		assertThat(jdbcConnectionDetails.getJdbcUrl()).isEqualTo(postgresContainer.getJdbcUrl());
		assertThat(jdbcConnectionDetails.getUsername()).isEqualTo(postgresContainer.getUsername());
		assertThat(jdbcConnectionDetails.getPassword()).isEqualTo(postgresContainer.getPassword());
	}

	@Test
	void r2dbcSelectOneRoundTrip() {
		Integer value = databaseClient.sql("SELECT 1 AS value")
				.map((row, metadata) -> row.get("value", Integer.class))
				.one()
				.block();

		assertThat(value).isEqualTo(1);
	}

}
