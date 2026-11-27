package com.vidacruz.clinical.infrastructure.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.reactive.function.client.WebClient;

import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.LLMRouter;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;
import com.vidacruz.clinical.domain.ports.VectorStore;
import com.vidacruz.clinical.domain.services.RAGService;
import com.vidacruz.clinical.infrastructure.adapters.FixtureEmbeddingAdapter;
import com.vidacruz.clinical.infrastructure.adapters.FixtureVectorStoreAdapter;
import com.vidacruz.clinical.infrastructure.adapters.GeminiHttpLLMAdapter;
import com.vidacruz.clinical.infrastructure.adapters.LlmCallMetrics;
import com.vidacruz.clinical.infrastructure.adapters.OllamaHttpLLMAdapter;
import com.vidacruz.clinical.infrastructure.adapters.OpenCodeHttpLLMAdapter;
import com.vidacruz.clinical.infrastructure.adapters.PgvectorVectorStoreAdapter;
import com.vidacruz.clinical.infrastructure.adapters.ResilientLLMRouter;
import com.vidacruz.clinical.infrastructure.db.VectorRepository;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Wires the RAG use case. The domain stays free of Spring annotations; this class is the only place that binds
 * ports to adapters. Bean names are deliberately distinct from Spring AI's auto-configured {@code vectorStore}.
 *
 * <p>{@code clinical.vector-store.mode} picks the {@code VectorStore}: {@code pgvector} (default, real JDBC adapter) or
 * {@code fixture} (in-memory, no database, for pipeline tests only). {@code rag.llm.chain} picks and orders the LLM
 * nodes; every node has its own circuit breaker from {@link Resilience4jConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@Import(Resilience4jConfiguration.class)
@EnableConfigurationProperties({ OllamaProperties.class, GeminiProperties.class, OpenCodeProperties.class,
		LLMRouterProperties.class, VectorStoreProperties.class })
class RAGConfiguration {

	@ConditionalOnProperty(name = "clinical.vector-store.mode", havingValue = "pgvector", matchIfMissing = true)
	@Bean(destroyMethod = "dispose")
	Scheduler pgvectorJdbcScheduler(VectorStoreProperties properties) {
		return Schedulers.newBoundedElastic(properties.scheduler().threadCap(), properties.scheduler().queuedTaskCap(),
				"pgvector-jdbc");
	}

	@ConditionalOnProperty(name = "clinical.vector-store.mode", havingValue = "pgvector", matchIfMissing = true)
	@Bean
	VectorRepository vectorRepository(DataSource dataSource) {
		return new VectorRepository(JdbcClient.create(dataSource));
	}

	@ConditionalOnProperty(name = "clinical.vector-store.mode", havingValue = "pgvector", matchIfMissing = true)
	@Bean
	VectorStore pgvectorStore(VectorRepository repository, Scheduler pgvectorJdbcScheduler) {
		return new PgvectorVectorStoreAdapter(repository, pgvectorJdbcScheduler);
	}

	@ConditionalOnProperty(name = "clinical.vector-store.mode", havingValue = "fixture")
	@Bean
	VectorStore fixtureStore() {
		return new FixtureVectorStoreAdapter();
	}

	@Bean
	EmbeddingProvider embeddingProvider() {
		return new FixtureEmbeddingAdapter();
	}

	@Bean
	NamedLLMProvider ollamaProvider(WebClient.Builder builder, OllamaProperties properties) {
		return new OllamaHttpLLMAdapter(builder, properties.baseUrl(), properties.chatModel(), properties.embeddingModel(),
				properties.timeoutSeconds());
	}

	@Bean
	NamedLLMProvider geminiProvider(WebClient.Builder builder, GeminiProperties properties) {
		return new GeminiHttpLLMAdapter(builder, properties.baseUrl(), properties.apiKey(), properties.chatModel(),
				properties.embeddingModel(), properties.timeoutSeconds());
	}

	@Bean
	NamedLLMProvider opencodeProvider(WebClient.Builder builder, OpenCodeProperties properties) {
		return new OpenCodeHttpLLMAdapter(builder, properties.baseUrl(), properties.apiKey(), properties.chatModel(),
				properties.embeddingModel(), properties.timeoutSeconds());
	}

	@Bean
	@ConditionalOnMissingBean(LLMRouter.class)
	LLMRouter llmRouter(LLMRouterProperties properties, List<NamedLLMProvider> providers,
			CircuitBreakerRegistry circuitBreakerRegistry, MeterRegistry meterRegistry) {
		Map<String, NamedLLMProvider> byName = providers.stream()
				.collect(Collectors.toMap(NamedLLMProvider::name, provider -> provider));
		List<NamedLLMProvider> ordered = new ArrayList<>();
		for (String name : properties.chain()) {
			NamedLLMProvider provider = byName.get(name);
			if (provider != null) {
				ordered.add(provider);
			}
		}
		return new ResilientLLMRouter(ordered, circuitBreakerRegistry, new LlmCallMetrics(meterRegistry),
				"clinical-assistant");
	}

	@Bean
	RAGService ragService(VectorStore vectorStore, EmbeddingProvider embeddingProvider, LLMRouter llmRouter) {
		return new RAGService(vectorStore, embeddingProvider, llmRouter);
	}
}
