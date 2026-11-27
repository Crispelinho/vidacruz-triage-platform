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

import com.vidacruz.clinical.domain.ports.ClinicalDocumentRepository;
import com.vidacruz.clinical.domain.ports.CorpusIndexSource;
import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.LLMRouter;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;
import com.vidacruz.clinical.domain.ports.VectorStore;
import com.vidacruz.clinical.domain.services.CorpusIndex;
import com.vidacruz.clinical.domain.services.CorpusIndexRefresher;
import com.vidacruz.clinical.domain.services.CorpusIngestionService;
import com.vidacruz.clinical.domain.services.RAGService;
import com.vidacruz.clinical.domain.services.RagRetrievalService;
import com.vidacruz.clinical.domain.services.TextChunker;
import com.vidacruz.clinical.infrastructure.adapters.FixtureVectorStoreAdapter;
import com.vidacruz.clinical.infrastructure.adapters.GeminiEmbeddingAdapter;
import com.vidacruz.clinical.infrastructure.adapters.GeminiHttpLLMAdapter;
import com.vidacruz.clinical.infrastructure.adapters.InMemoryClinicalDocumentAdapter;
import com.vidacruz.clinical.infrastructure.adapters.JdbcClinicalDocumentAdapter;
import com.vidacruz.clinical.infrastructure.adapters.LexicalEmbeddingAdapter;
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
 * <p>{@code clinical.vector-store.mode} picks the {@code VectorStore} and the document repository: {@code pgvector}
 * (default, real JDBC adapters) or {@code fixture} (in-memory, no database, for pipeline tests only).
 * {@code rag.llm.chain} picks and orders the LLM nodes; every node has its own circuit breaker from
 * {@link Resilience4jConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@Import(Resilience4jConfiguration.class)
@EnableConfigurationProperties({ OllamaProperties.class, GeminiProperties.class, GeminiEmbeddingProperties.class,
		OpenCodeProperties.class, LLMRouterProperties.class, VectorStoreProperties.class })
class RAGConfiguration {

	@ConditionalOnProperty(name = "clinical.vector-store.mode", havingValue = "pgvector", matchIfMissing = true)
	@Bean(destroyMethod = "dispose")
	Scheduler pgvectorJdbcScheduler(VectorStoreProperties properties) {
		return Schedulers.newBoundedElastic(properties.scheduler().threadCap(), properties.scheduler().queuedTaskCap(),
				"pgvector-jdbc");
	}

	@ConditionalOnProperty(name = "clinical.vector-store.mode", havingValue = "pgvector", matchIfMissing = true)
	@Bean
	VectorRepository evidenceVectorRepository(DataSource dataSource, VectorStoreProperties properties) {
		return new VectorRepository(JdbcClient.create(dataSource), properties.dimensions());
	}

	@ConditionalOnProperty(name = "clinical.vector-store.mode", havingValue = "pgvector", matchIfMissing = true)
	@Bean
	VectorStore pgvectorVectorStoreAdapter(VectorRepository evidenceVectorRepository, Scheduler pgvectorJdbcScheduler) {
		return new PgvectorVectorStoreAdapter(evidenceVectorRepository, pgvectorJdbcScheduler);
	}

	@ConditionalOnProperty(name = "clinical.vector-store.mode", havingValue = "pgvector", matchIfMissing = true)
	@Bean
	ClinicalDocumentRepository jdbcClinicalDocumentAdapter(DataSource dataSource, Scheduler pgvectorJdbcScheduler) {
		return new JdbcClinicalDocumentAdapter(JdbcClient.create(dataSource), pgvectorJdbcScheduler);
	}

	@Bean
	@ConditionalOnProperty(name = "clinical.vector-store.mode", havingValue = "fixture")
	VectorStore fixtureVectorStoreAdapter() {
		return new FixtureVectorStoreAdapter();
	}

	@Bean
	@ConditionalOnProperty(name = "clinical.vector-store.mode", havingValue = "fixture")
	ClinicalDocumentRepository inMemoryClinicalDocumentAdapter() {
		return new InMemoryClinicalDocumentAdapter();
	}

	@Bean
	@ConditionalOnMissingBean(WebClient.Builder.class)
	WebClient.Builder webClientBuilder() {
		return WebClient.builder();
	}

	@Bean
	NamedLLMProvider ollamaNode(WebClient.Builder builder, OllamaProperties properties) {
		return new OllamaHttpLLMAdapter(builder, properties);
	}

	@Bean
	NamedLLMProvider geminiNode(WebClient.Builder builder, GeminiProperties properties) {
		return new GeminiHttpLLMAdapter(builder, properties);
	}

	@Bean
	NamedLLMProvider opencodeNode(WebClient.Builder builder, OpenCodeProperties properties) {
		return new OpenCodeHttpLLMAdapter(builder, properties);
	}

	@Bean
	LLMRouter llmRouter(List<NamedLLMProvider> providers, CircuitBreakerRegistry registry,
			LLMRouterProperties properties, MeterRegistry meterRegistry) {
		Map<String, NamedLLMProvider> byKey = providers.stream()
				.collect(Collectors.toMap(NamedLLMProvider::key, provider -> provider));
		List<NamedLLMProvider> ordered = new ArrayList<>();
		for (String key : properties.providerKeys()) {
			NamedLLMProvider provider = byKey.get(key);
			if (provider == null) {
				throw new IllegalArgumentException("rag.llm.chain names unknown provider: " + key);
			}
			ordered.add(provider);
		}
		return new ResilientLLMRouter(ordered, registry, properties.timeout(), new LlmCallMetrics(meterRegistry));
	}

	/**
	 * Exactly one embedding bean is registered. The lexical adapter remains the default because the seeded vectors
	 * were built with it; selecting {@code gemini} requires a full re-ingestion of the corpus with that model.
	 */
	@Bean
	@ConditionalOnProperty(name = "clinical.vector-store.embedding-provider", havingValue = "lexical", matchIfMissing = true)
	EmbeddingProvider lexicalEmbeddingAdapter(VectorStoreProperties properties) {
		return new LexicalEmbeddingAdapter(properties.dimensions());
	}

	@Bean
	@ConditionalOnProperty(name = "clinical.vector-store.embedding-provider", havingValue = "gemini")
	EmbeddingProvider geminiEmbeddingAdapter(WebClient.Builder builder, GeminiEmbeddingProperties properties) {
		return new GeminiEmbeddingAdapter(builder, properties);
	}

	@Bean
	CorpusIndex corpusIndex() {
		return new CorpusIndex();
	}

	/** Explicit projection: both store adapters implement this port, but their bean methods return VectorStore. */
	@Bean
	CorpusIndexSource corpusIndexSource(VectorStore vectorStore) {
		if (!(vectorStore instanceof CorpusIndexSource source)) {
			throw new IllegalStateException("Configured VectorStore cannot supply the citation corpus index");
		}
		// Return a projection, not the adapter instance: both adapters also implement VectorStore, so exposing
		// the same object here would register a second VectorStore candidate and break single-bean injection.
		return source::protocolSections;
	}

	@Bean
	CorpusIndexRefresher corpusIndexRefresher(CorpusIndexSource corpusIndexSource, CorpusIndex corpusIndex) {
		return new CorpusIndexRefresher(corpusIndexSource, corpusIndex);
	}

	/** Fail fast if the citation index cannot be loaded: answers would otherwise be rejected or ungrounded. */
	@Bean
	org.springframework.boot.ApplicationRunner corpusIndexRunner(CorpusIndexRefresher refresher) {
		return args -> refresher.refresh().block();
	}

	@Bean
	RAGService ragService(EmbeddingProvider embeddingProvider, VectorStore vectorStore, LLMRouter llmRouter,
			CorpusIndex corpusIndex, VectorStoreProperties properties) {
		return new RAGService(embeddingProvider, vectorStore, llmRouter, corpusIndex,
				properties.relevanceThreshold());
	}

	@Bean
	TextChunker textChunker() {
		return new TextChunker();
	}

	@Bean
	CorpusIngestionService corpusIngestionService(TextChunker textChunker, EmbeddingProvider embeddingProvider,
			VectorStore vectorStore, ClinicalDocumentRepository clinicalDocumentRepository, CorpusIndex corpusIndex) {
		return new CorpusIngestionService(textChunker, embeddingProvider, vectorStore, clinicalDocumentRepository,
				corpusIndex);
	}

	@Bean
	RagRetrievalService ragRetrievalService(EmbeddingProvider embeddingProvider, VectorStore vectorStore,
			ClinicalDocumentRepository clinicalDocumentRepository) {
		return new RagRetrievalService(embeddingProvider, vectorStore, clinicalDocumentRepository);
	}

}
