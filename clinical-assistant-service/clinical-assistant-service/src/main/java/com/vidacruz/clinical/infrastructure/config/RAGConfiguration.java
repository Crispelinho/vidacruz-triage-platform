package com.vidacruz.clinical.infrastructure.config;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.LLMProvider;
import com.vidacruz.clinical.domain.ports.VectorStore;
import com.vidacruz.clinical.domain.services.RAGService;
import com.vidacruz.clinical.infrastructure.adapters.FixtureEmbeddingAdapter;
import com.vidacruz.clinical.infrastructure.adapters.OllamaLLMAdapter;
import com.vidacruz.clinical.infrastructure.adapters.PgvectorVectorStoreAdapter;
import com.vidacruz.clinical.infrastructure.db.VectorRepository;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Wires the RAG use case. The domain stays free of Spring annotations; this class is the only place that binds
 * ports to adapters. Bean names are deliberately distinct from Spring AI's auto-configured {@code vectorStore}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ OllamaProperties.class, VectorStoreProperties.class })
class RAGConfiguration {

	@Bean(destroyMethod = "dispose")
	Scheduler pgvectorJdbcScheduler(VectorStoreProperties properties) {
		return Schedulers.newBoundedElastic(properties.scheduler().threadCap(), properties.scheduler().queuedTaskCap(),
				"pgvector-jdbc");
	}

	@Bean
	VectorRepository evidenceVectorRepository(DataSource dataSource, VectorStoreProperties properties) {
		return new VectorRepository(JdbcClient.create(dataSource), properties.dimensions());
	}

	@Bean
	VectorStore pgvectorVectorStoreAdapter(VectorRepository evidenceVectorRepository, Scheduler pgvectorJdbcScheduler) {
		return new PgvectorVectorStoreAdapter(evidenceVectorRepository, pgvectorJdbcScheduler);
	}

	@Bean
	LLMProvider ollamaLlmAdapter(OllamaProperties properties) {
		return new OllamaLLMAdapter(properties);
	}

	@Bean
	EmbeddingProvider fixtureEmbeddingAdapter(VectorStoreProperties properties) {
		return new FixtureEmbeddingAdapter(properties.dimensions());
	}

	@Bean
	RAGService ragService(EmbeddingProvider fixtureEmbeddingAdapter, VectorStore pgvectorVectorStoreAdapter,
			LLMProvider ollamaLlmAdapter) {
		return new RAGService(fixtureEmbeddingAdapter, pgvectorVectorStoreAdapter, ollamaLlmAdapter);
	}

}
