package com.vidacruz.triageservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class TriageServiceApplicationTests {

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private DatabaseClient databaseClient;

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
		webTestClient.get()
				.uri("/triage/pacientes/00000000-0000-0000-0000-000000000000")
				.exchange()
				.expectStatus().isUnauthorized();
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
