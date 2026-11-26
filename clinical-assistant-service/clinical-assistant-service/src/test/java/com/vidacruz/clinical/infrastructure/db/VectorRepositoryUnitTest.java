package com.vidacruz.clinical.infrastructure.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.vidacruz.clinical.domain.models.EvidenceFragment;

/** Pure-Java checks of the repository: vector literal format and argument validation. No database. */
class VectorRepositoryUnitTest {

	private final JdbcClient jdbc = mock(JdbcClient.class);

	private final VectorRepository repository = new VectorRepository(jdbc, 3);

	@Test
	void vectorLiteralRoundTrips() {
		float[] original = { 0.5f, -1.25f, 3f, 1.0E-5f };

		String literal = VectorRepository.toVectorLiteral(original);

		assertThat(literal).startsWith("[").endsWith("]");
		assertThat(VectorRepository.parseVector(literal)).containsExactly(original);
	}

	@Test
	void parsesPostgresTextOutput() {
		assertThat(VectorRepository.parseVector("[1,2.5,-3]")).containsExactly(1f, 2.5f, -3f);
		assertThat(VectorRepository.parseVector("[]")).isEmpty();
	}

	@Test
	void upsertRejectsWrongDimensionsBeforeTouchingTheDatabase() {
		EvidenceFragment wrong = EvidenceFragment.create(UUID.randomUUID(), 0, null, null, "texto",
				new float[] { 1f, 2f });

		assertThatThrownBy(() -> repository.upsert(wrong)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("expected 3");
		verifyNoInteractions(jdbc);
	}

	@Test
	void searchRejectsWrongDimensionsAndNonPositiveTopK() {
		assertThatThrownBy(() -> repository.searchSimilar(new float[] { 1f }, 3))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expected 3");
		assertThatThrownBy(() -> repository.searchSimilar(new float[] { 1f, 2f, 3f }, 0))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("topK");
		verifyNoInteractions(jdbc);
	}

}
