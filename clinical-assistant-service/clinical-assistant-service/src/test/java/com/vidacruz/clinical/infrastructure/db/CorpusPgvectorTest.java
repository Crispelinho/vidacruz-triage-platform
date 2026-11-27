package com.vidacruz.clinical.infrastructure.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.services.CorpusIngestionService;
import com.vidacruz.clinical.domain.services.RagRetrievalService;
import com.vidacruz.clinical.domain.services.TextChunker;
import com.vidacruz.clinical.infrastructure.adapters.JdbcClinicalDocumentAdapter;
import com.vidacruz.clinical.infrastructure.adapters.PgvectorVectorStoreAdapter;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * RF-04A against a real pgvector: ingestion, idempotency and the RN-05 gate with exact cosine similarities. The
 * embedding is hardcoded, so no model is involved. Skipped, not failed, when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
class CorpusPgvectorTest {

	private static final int DIMENSIONS = 768;

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	private static JdbcClient jdbc;

	private static Scheduler scheduler;

	private static CorpusIngestionService ingestion;

	private static RagRetrievalService retrieval;

	/** Topic keywords own an axis. The query "tilted heart" has cosine 0.8 with any heart chunk. */
	private static final EmbeddingProvider EMBEDDINGS = text -> Mono.fromSupplier(() -> {
		float[] vector = new float[DIMENSIONS];
		if (text.equals("tilted heart")) {
			vector[0] = 0.8f;
			vector[10] = 0.6f;
		}
		else {
			vector[text.contains("heart") ? 0 : 1] = 1f;
		}
		return vector;
	});

	@BeforeAll
	static void setUp() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
				POSTGRES.getUsername(), POSTGRES.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		jdbc = JdbcClient.create(dataSource);
		scheduler = Schedulers.newBoundedElastic(2, 50, "corpus-pgvector-test");
		var store = new PgvectorVectorStoreAdapter(new VectorRepository(jdbc, DIMENSIONS), scheduler);
		var documents = new JdbcClinicalDocumentAdapter(jdbc, scheduler);
		ingestion = new CorpusIngestionService(new TextChunker(4, 0.5), EMBEDDINGS, store, documents);
		retrieval = new RagRetrievalService(EMBEDDINGS, store, documents);
	}

	@AfterAll
	static void tearDown() {
		scheduler.dispose();
	}

	@BeforeEach
	void clean() {
		jdbc.sql("TRUNCATE clinical_documents, evidence_fragments").update();
	}

	private long count(String table) {
		return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
	}

	@Test
	void ingestPersistsDocumentAndChunksAndIsIdempotent() {
		String content = "heart ".repeat(10).trim(); // 10 words, window 4, step 2 -> 4 chunks

		var first = ingestion.ingestDocument("Cardiology", content).block();
		var second = ingestion.ingestDocument("Cardiology", content).block();

		assertThat(first.chunksCount()).isEqualTo(4);
		assertThat(second).isEqualTo(first);
		assertThat(count("clinical_documents")).isEqualTo(1);
		assertThat(count("evidence_fragments")).isEqualTo(4);
		assertThat(jdbc.sql("SELECT DISTINCT vector_dims(embedding) FROM evidence_fragments").query(Integer.class).list())
				.containsExactly(DIMENSIONS);
		assertThat(jdbc.sql("SELECT chunks_count FROM clinical_documents").query(Integer.class).single()).isEqualTo(4);
	}

	@Test
	void rn05GateKeepsOnlyChunksAtOrAboveTheThresholdAndNamesTheSource() {
		ingestion.ingestDocument("Cardiology", "heart one two three").block();
		ingestion.ingestDocument("Neurology", "brain one two three").block();

		var atDefault = retrieval.retrieve("tilted heart").block();
		var atStrict = retrieval.retrieve("tilted heart", 0.95).block();

		assertThat(atDefault).hasSize(1);
		assertThat(atDefault.get(0).source()).isEqualTo("Cardiology");
		assertThat(atDefault.get(0).similarity()).isBetween(0.79, 0.81);
		assertThat(atStrict).as("0.8 < 0.95: nothing passes, nothing is invented").isEmpty();
	}

	@Test
	void retrievalNeverReturnsMoreThanFiveChunks() {
		// 40 words, window 4, step 2 -> 19 chunks, all on the heart axis.
		String content = "heart ".repeat(40).trim();
		ingestion.ingestDocument("Long", content).block();

		assertThat(retrieval.retrieve("tilted heart", 0.5).block()).hasSize(RagRetrievalService.TOP_K);
	}

}
