package com.vidacruz.clinical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.LLMRouter;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;
import com.vidacruz.clinical.domain.ports.VectorStore;
import com.vidacruz.clinical.domain.services.RAGService;
import com.vidacruz.clinical.infrastructure.adapters.LexicalEmbeddingAdapter;
import com.vidacruz.clinical.infrastructure.adapters.OllamaHttpLLMAdapter;
import com.vidacruz.clinical.infrastructure.adapters.PgvectorVectorStoreAdapter;
import com.vidacruz.clinical.infrastructure.adapters.ResilientLLMRouter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.scheduler.Scheduler;

/** Wiring and property binding without a database: the DataSource is a mock. */
class RAGConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(RAGConfiguration.class)
			.withBean(DataSource.class, () -> mock(DataSource.class))
			.withBean(MeterRegistry.class, SimpleMeterRegistry::new);

	@Test
	void wiresPortsToTheFailoverChain() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBeansOfType(NamedLLMProvider.class)).hasSize(3);
			assertThat(context.getBean("ollamaNode", NamedLLMProvider.class)).isInstanceOf(OllamaHttpLLMAdapter.class);
			assertThat(context.getBean(LLMRouter.class)).isInstanceOf(ResilientLLMRouter.class);
			assertThat(context.getBean(EmbeddingProvider.class)).isInstanceOf(LexicalEmbeddingAdapter.class);
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(PgvectorVectorStoreAdapter.class);
			assertThat(context).hasSingleBean(RAGService.class);
		});
	}

	@Test
	void appliesDocumentedDefaultsWhenNothingIsConfigured() {
		runner.run(context -> {
			OllamaProperties ollama = context.getBean(OllamaProperties.class);
			assertThat(ollama.url()).isEqualTo("http://localhost:11434");
			assertThat(ollama.timeout()).isEqualTo(Duration.ofSeconds(15));
			assertThat(ollama.fixture().latency()).isEqualTo(Duration.ofMillis(1200));

			LLMRouterProperties chain = context.getBean(LLMRouterProperties.class);
			assertThat(chain.providerKeys()).containsExactly("ollama", "gemini", "opencode");
			assertThat(chain.timeout()).isEqualTo(Duration.ofSeconds(15));

			VectorStoreProperties vector = context.getBean(VectorStoreProperties.class);
			assertThat(vector.dimensions()).isEqualTo(768);
			assertThat(vector.scheduler().threadCap()).isEqualTo(4);
			assertThat(vector.scheduler().queuedTaskCap()).isEqualTo(100);
			assertThat(vector.relevanceThreshold()).isEqualTo(VectorStoreProperties.CALIBRATED_RELEVANCE_THRESHOLD);
		});
	}

	@Test
	void bindsOverridesFromProperties() {
		runner.withPropertyValues("ollama.url=http://gpu:11434", "ollama.timeout=5s", "ollama.fixture.latency=50ms",
				"clinical.vector-store.dimensions=1024", "clinical.vector-store.scheduler.thread-cap=2",
				"clinical.vector-store.relevance-threshold=0.4")
				.run(context -> {
					assertThat(context.getBean(OllamaProperties.class).url()).isEqualTo("http://gpu:11434");
					assertThat(context.getBean(OllamaProperties.class).timeout()).isEqualTo(Duration.ofSeconds(5));
					assertThat(context.getBean(VectorStoreProperties.class).dimensions()).isEqualTo(1024);
					assertThat(context.getBean(VectorStoreProperties.class).scheduler().threadCap()).isEqualTo(2);
					assertThat(context.getBean(VectorStoreProperties.class).relevanceThreshold()).isEqualTo(0.4d);
				});
	}

	@Test
	void chainOrderAndTimeoutAreConfigurable() {
		runner.withPropertyValues("rag.llm.chain=opencode,ollama", "rag.llm.timeout=2s").run(context -> {
			LLMRouterProperties chain = context.getBean(LLMRouterProperties.class);
			assertThat(chain.providerKeys()).containsExactly("opencode", "ollama");
			assertThat(chain.timeout()).isEqualTo(Duration.ofSeconds(2));
			assertThat(context).hasNotFailed();
		});
	}

	@Test
	void invalidPropertiesFailStartup() {
		runner.withPropertyValues("clinical.vector-store.dimensions=0")
				.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("ollama.timeout=0s").run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("rag.llm.chain=unknown").run(context -> assertThat(context).hasFailed());
	}

	@Test
	void jdbcSchedulerIsDisposedWhenTheContextCloses() {
		Scheduler[] scheduler = new Scheduler[1];

		runner.run(context -> scheduler[0] = context.getBean(Scheduler.class));

		assertThat(scheduler[0].isDisposed()).isTrue();
	}

}
