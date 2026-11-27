package com.vidacruz.clinical.infrastructure.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Database isolation (ADR-14, ADR-04): the assistant database holds only its own schema, with no triage tables, and the
 * service configuration never points at the triage database. Skipped, not failed, without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class ClinicalSchemaIsolationTest {

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	private static JdbcClient jdbc;

	@BeforeAll
	static void migrate() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
				POSTGRES.getUsername(), POSTGRES.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		jdbc = JdbcClient.create(dataSource);
	}

	@Test
	void migrationsCreateOnlyTheClinicalCorpusTables() {
		List<String> tables = jdbc.sql("SELECT table_name FROM information_schema.tables "
				+ "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name")
				.query(String.class)
				.list();

		// V5 (RF-04A) adds the document registry; there are still no triage tables.
		assertThat(tables).containsExactlyInAnyOrder("clinical_documents", "evidence_fragments",
				"flyway_schema_history");
	}

	@Test
	void embeddingColumnIsVector768WithAnHnswCosineIndex() {
		String type = jdbc.sql("SELECT format_type(a.atttypid, a.atttypmod) FROM pg_attribute a "
				+ "WHERE a.attrelid = 'evidence_fragments'::regclass AND a.attname = 'embedding'")
				.query(String.class)
				.single();
		String indexDefinition = jdbc.sql("SELECT indexdef FROM pg_indexes "
				+ "WHERE tablename = 'evidence_fragments' AND indexname = 'idx_evidence_fragments_embedding'")
				.query(String.class)
				.single();

		assertThat(type).isEqualTo("vector(768)");
		assertThat(indexDefinition).contains("USING hnsw").contains("vector_cosine_ops");
	}

	@Test
	void serviceConfigurationAndMigrationsNeverMentionTheTriageDatabase() throws IOException {
		for (String resource : List.of("application.yaml", "db/migration/V3__pgvector_evidence_schema.sql")) {
			try (InputStream in = new ClassPathResource(resource).getInputStream()) {
				String content = new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
				assertThat(content).as(resource).doesNotContain("triage");
			}
		}
	}

}
