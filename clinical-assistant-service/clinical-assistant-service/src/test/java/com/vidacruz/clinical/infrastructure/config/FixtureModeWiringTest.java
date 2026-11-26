package com.vidacruz.clinical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.vidacruz.clinical.domain.ports.VectorStore;
import com.vidacruz.clinical.domain.services.RAGService;
import com.vidacruz.clinical.infrastructure.adapters.FixtureVectorStoreAdapter;
import com.vidacruz.clinical.infrastructure.adapters.PgvectorVectorStoreAdapter;
import com.vidacruz.clinical.infrastructure.db.VectorRepository;

import reactor.core.scheduler.Scheduler;

/** {@code clinical.vector-store.mode} swaps the store; in fixture mode no DataSource and no JDBC beans exist. */
class FixtureModeWiringTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(RAGConfiguration.class);

	@Test
	void fixtureModeNeedsNoDatabaseAndWiresTheInMemoryStore() {
		runner.withPropertyValues("clinical.vector-store.mode=fixture").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(FixtureVectorStoreAdapter.class);
			assertThat(context).hasSingleBean(RAGService.class);
			assertThat(context).doesNotHaveBean(VectorRepository.class);
			assertThat(context).doesNotHaveBean(Scheduler.class);
		});
	}

	@Test
	void pgvectorModeIsTheDefaultAndNeedsADataSource() {
		runner.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void explicitPgvectorModeWiresTheJdbcAdapter() {
		runner.withBean(javax.sql.DataSource.class, () -> org.mockito.Mockito.mock(javax.sql.DataSource.class))
				.withPropertyValues("clinical.vector-store.mode=pgvector")
				.run(context -> assertThat(context.getBean(VectorStore.class))
						.isInstanceOf(PgvectorVectorStoreAdapter.class));
	}

}
