package com.vidacruz.clinical.infrastructure.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.vidacruz.clinical.domain.models.EvidenceFragment;

/**
 * Runs the real {@code V3__pgvector_evidence_schema.sql} and the repository SQL against pgvector. Skipped, not failed,
 * when Docker is unavailable, so the suite still passes without external dependencies.
 */
@Testcontainers(disabledWithoutDocker = true)
class VectorRepositoryTest {

	private static final int DIMENSIONS = 768;

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	private static JdbcClient jdbc;

	private static VectorRepository repository;

	@BeforeAll
	static void migrate() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
				POSTGRES.getUsername(), POSTGRES.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		jdbc = JdbcClient.create(dataSource);
		repository = new VectorRepository(jdbc, DIMENSIONS);
	}

	@BeforeEach
	void clean() {
		jdbc.sql("TRUNCATE evidence_fragments").update();
	}

	/** A unit vector along one axis, optionally leaning toward another, never the zero vector. */
	private static float[] vector(int axis, int leanAxis, float lean) {
		float[] vector = new float[DIMENSIONS];
		vector[axis] = 1f;
		if (leanAxis >= 0) {
			vector[leanAxis] = lean;
		}
		return vector;
	}

	private static EvidenceFragment fragment(UUID documentId, int chunk, String protocol, float[] embedding) {
		return EvidenceFragment.create(documentId, chunk, protocol, "Medidas iniciales", "texto " + chunk,
				embedding);
	}

	@Test
	void upsertThenFindByIdRoundTripsEveryField() {
		EvidenceFragment saved = repository
				.upsert(fragment(UUID.randomUUID(), 0, "PROTO-001", vector(0, 1, 0.25f)));

		assertThat(repository.findById(saved.id())).contains(saved);
	}

	@Test
	void protocolSectionsAreDistinctAndSkipFragmentsWithoutMetadata() {
		repository.upsert(fragment(UUID.randomUUID(), 0, "PROTO-001", vector(0, -1, 0)));
		repository.upsert(fragment(UUID.randomUUID(), 1, "PROTO-001", vector(0, -1, 0)));
		repository.upsert(EvidenceFragment.create(UUID.randomUUID(), 2, null, "sin protocolo", "texto",
				vector(0, -1, 0)));

		assertThat(repository.protocolSections()).hasSize(1)
				.first().satisfies(pair -> {
					assertThat(pair.protocolo()).isEqualTo("PROTO-001");
					assertThat(pair.seccion()).isEqualTo("Medidas iniciales");
				});
	}

	@Test
	void nullableMetadataRoundTripsAsNull() {
		EvidenceFragment saved = repository.upsert(EvidenceFragment.create(UUID.randomUUID(), 0, null, null, "texto",
				vector(0, -1, 0)));

		assertThat(repository.findById(saved.id())).hasValueSatisfying(found -> {
			assertThat(found.protocol()).isNull();
			assertThat(found.section()).isNull();
		});
	}

	@Test
	void upsertWithTheSameIdUpdatesInsteadOfDuplicating() {
		EvidenceFragment original = repository.upsert(fragment(UUID.randomUUID(), 0, "PROTO-001", vector(0, -1, 0)));
		EvidenceFragment changed = new EvidenceFragment(original.id(), original.documentId(), 0, "PROTO-002",
				"Seguimiento", "texto nuevo", vector(1, -1, 0));

		repository.upsert(changed);

		assertThat(repository.findById(original.id())).contains(changed);
		assertThat(jdbc.sql("SELECT count(*) FROM evidence_fragments").query(Long.class).single()).isEqualTo(1L);
	}

	@Test
	void deleteByIdReportsWhetherARowWasRemoved() {
		EvidenceFragment saved = repository.upsert(fragment(UUID.randomUUID(), 0, "PROTO-001", vector(0, -1, 0)));

		assertThat(repository.deleteById(saved.id())).isTrue();
		assertThat(repository.deleteById(saved.id())).isFalse();
		assertThat(repository.findById(saved.id())).isEmpty();
	}

	@Test
	void findByIdIsEmptyForUnknownId() {
		assertThat(repository.findById(UUID.randomUUID())).isEmpty();
	}

	@Test
	void searchSimilarOrdersByCosineDistanceAndHonorsTopK() {
		UUID document = UUID.randomUUID();
		EvidenceFragment exact = repository.upsert(fragment(document, 0, "PROTO-001", vector(0, -1, 0)));
		EvidenceFragment near = repository.upsert(fragment(document, 1, "PROTO-001", vector(0, 1, 0.5f)));
		EvidenceFragment orthogonal = repository.upsert(fragment(document, 2, "PROTO-002", vector(1, -1, 0)));

		List<EvidenceFragment> top3 = repository.searchSimilar(vector(0, -1, 0), 3);
		List<EvidenceFragment> top1 = repository.searchSimilar(vector(0, -1, 0), 1);

		assertThat(top3).extracting(EvidenceFragment::id).containsExactly(exact.id(), near.id(), orthogonal.id());
		assertThat(top1).extracting(EvidenceFragment::id).containsExactly(exact.id());
	}

	@Test
	void searchOnAnEmptyCorpusReturnsAnEmptyList() {
		assertThat(repository.searchSimilar(vector(0, -1, 0), 4)).isEmpty();
	}

	@Test
	void secondFragmentAtTheSameDocumentChunkPositionViolatesTheUniqueKey() {
		UUID document = UUID.randomUUID();
		repository.upsert(fragment(document, 0, "PROTO-001", vector(0, -1, 0)));

		assertThatThrownBy(() -> repository.upsert(fragment(document, 0, "PROTO-001", vector(1, -1, 0))))
				.isInstanceOf(DuplicateKeyException.class);
	}

}
