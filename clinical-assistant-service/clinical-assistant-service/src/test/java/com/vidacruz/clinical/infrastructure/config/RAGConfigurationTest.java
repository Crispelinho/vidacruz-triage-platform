package com.vidacruz.clinical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.LLMProvider;
import com.vidacruz.clinical.domain.ports.VectorStore;
import com.vidacruz.clinical.domain.services.RAGService;
import com.vidacruz.clinical.infrastructure.adapters.FixtureEmbeddingAdapter;
import com.vidacruz.clinical.infrastructure.adapters.OllamaLLMAdapter;
import com.vidacruz.clinical.infrastructure.adapters.PgvectorVectorStoreAdapter;

import reactor.core.scheduler.Scheduler;

/** Wiring and property binding without a database: the DataSource is a mock. */
class RAGConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(RAGConfiguration.class)
			.withBean(DataSource.class, () -> mock(DataSource.class));

	@Test
	void wiresPortsToTheScaffoldAdapters() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(LLMProvider.class)).isInstanceOf(OllamaLLMAdapter.class);
			assertThat(context.getBean(EmbeddingProvider.class)).isInstanceOf(FixtureEmbeddingAdapter.class);
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(PgvectorVectorStoreAdapter.class);
			assertThat(context).hasSingleBean(RAGService.class);
		});
	}

	@Test
	void appliesDocumentedDefaultsWhenNothingIsConfigured() {
		runner.run(context -> {
			OllamaProperties ollama = context.getBean(OllamaProperties.class);
			assertThat(ollama.url()).isEqualTo("http://localhost:11434");
			assertThat(ollama.timeout()).isEqualTo(Duration.ofSeconds(30));
			assertThat(ollama.fixture().latency()).isEqualTo(Duration.ofMillis(1200));

			VectorStoreProperties vector = context.getBean(VectorStoreProperties.class);
			assertThat(vector.dimensions()).isEqualTo(768);
			assertThat(vector.scheduler().threadCap()).isEqualTo(4);
			assertThat(vector.scheduler().queuedTaskCap()).isEqualTo(100);
		});
	}

	@Test
	void bindsOverridesFromProperties() {
		runner.withPropertyValues("ollama.url=http://gpu:11434", "ollama.timeout=5s", "ollama.fixture.latency=50ms",
				"clinical.vector-store.dimensions=1024", "clinical.vector-store.scheduler.thread-cap=2")
				.run(context -> {
					assertThat(context.getBean(OllamaProperties.class).url()).isEqualTo("http://gpu:11434");
					assertThat(context.getBean(OllamaProperties.class).timeout()).isEqualTo(Duration.ofSeconds(5));
					assertThat(context.getBean(VectorStoreProperties.class).dimensions()).isEqualTo(1024);
					assertThat(context.getBean(VectorStoreProperties.class).scheduler().threadCap()).isEqualTo(2);
				});
	}

	@Test
	void invalidPropertiesFailStartup() {
		runner.withPropertyValues("clinical.vector-store.dimensions=0")
				.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("ollama.timeout=0s").run(context -> assertThat(context).hasFailed());
	}

	@Test
	void jdbcSchedulerIsDisposedWhenTheContextCloses() {
		Scheduler[] scheduler = new Scheduler[1];

		runner.run(context -> scheduler[0] = context.getBean(Scheduler.class));

		assertThat(scheduler[0].isDisposed()).isTrue();
	}

}
