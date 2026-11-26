package com.vidacruz.clinical.contract;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.vidacruz.clinical.domain.ports.VectorStore;
import com.vidacruz.clinical.infrastructure.adapters.PgvectorVectorStoreAdapter;
import com.vidacruz.clinical.infrastructure.db.VectorRepository;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** The same contract against the real adapter and a real pgvector. Skipped, not failed, without Docker. */
@Testcontainers(disabledWithoutDocker = true)
class PgvectorVectorStoreContractTest extends VectorStoreContract {

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	private static JdbcClient jdbc;

	private static Scheduler scheduler;

	private static VectorStore store;

	@BeforeAll
	static void migrate() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
				POSTGRES.getUsername(), POSTGRES.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		jdbc = JdbcClient.create(dataSource);
		scheduler = Schedulers.newBoundedElastic(2, 10, "pgvector-contract-test");
		store = new PgvectorVectorStoreAdapter(new VectorRepository(jdbc, DIMENSIONS), scheduler);
	}

	@AfterAll
	static void dispose() {
		scheduler.dispose();
	}

	@BeforeEach
	void clean() {
		jdbc.sql("TRUNCATE evidence_fragments").update();
	}

	@Override
	protected VectorStore store() {
		return store;
	}

}
