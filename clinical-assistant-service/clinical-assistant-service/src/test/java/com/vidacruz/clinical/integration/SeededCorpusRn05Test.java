package com.vidacruz.clinical.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.flywaydb.core.Flyway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.LLMCompletion;
import com.vidacruz.clinical.domain.models.PromptRequest;
import com.vidacruz.clinical.domain.models.RAGResponse;
import com.vidacruz.clinical.domain.models.ScoredEvidenceFragment;
import com.vidacruz.clinical.domain.ports.LLMRouter;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;
import com.vidacruz.clinical.domain.ports.VectorStore;
import com.vidacruz.clinical.domain.services.CorpusIndex;
import com.vidacruz.clinical.domain.services.RAGService;
import com.vidacruz.clinical.infrastructure.adapters.LexicalEmbeddingAdapter;
import com.vidacruz.clinical.infrastructure.adapters.PgvectorVectorStoreAdapter;
import com.vidacruz.clinical.infrastructure.adapters.PiiRedactingLLMProvider;
import com.vidacruz.clinical.infrastructure.config.VectorStoreProperties;
import com.vidacruz.clinical.infrastructure.db.VectorRepository;
import com.vidacruz.clinical.infrastructure.adapters.JacksonGroundedAnswerDecoder;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * RN-05 acceptance against the seeded corpus in a real pgvector database. Retrieval uses the production lexical
 * adapter; the LLM is a stub whose answer only carries a valid marker. No patient data is used.
 */
@Testcontainers(disabledWithoutDocker = true)
class SeededCorpusRn05Test {

	private static final int DIMENSIONS = 768;

	private static final double THRESHOLD = VectorStoreProperties.CALIBRATED_RELEVANCE_THRESHOLD;

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	private static Scheduler scheduler;

	private static VectorStore store;

	private static CorpusIndex corpusIndex;

	private final LexicalEmbeddingAdapter embeddings = new LexicalEmbeddingAdapter(DIMENSIONS);

	@BeforeAll
	static void migrate() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
				POSTGRES.getUsername(), POSTGRES.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		scheduler = Schedulers.newBoundedElastic(2, 10, "rn05-acceptance-test");
		PgvectorVectorStoreAdapter pgvector = new PgvectorVectorStoreAdapter(
				new VectorRepository(JdbcClient.create(dataSource), DIMENSIONS), scheduler);
		store = pgvector;
		corpusIndex = new CorpusIndex();
		corpusIndex.refresh(pgvector.protocolSections().block());
	}

	@AfterAll
	static void dispose() {
		scheduler.dispose();
	}

	@Test
	void q1HypertensionRetrievesProto001AndCitesIt() {
		String question = "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?";
		List<ScoredEvidenceFragment> scored = retrieve(question);

		assertThat(scored.get(0).fragment().protocol()).isEqualTo("PROTO-001");
		assertThat(scored.get(0).score()).as("Q1 top score").isGreaterThanOrEqualTo(THRESHOLD);

		RAGResponse response = service((q, evidence) -> Mono
				.just(new LLMCompletion("stub", "stub", jsonAnswer(scored.get(0).fragment()))))
				.answer(new PromptRequest(question, PromptRequest.DEFAULT_TOP_K))
				.block();

		assertThat(response.sources()).extracting(EvidenceFragment::protocol).containsExactly("PROTO-001");
		assertThat(response.citations()).hasSize(1);
		assertThat(response.answer()).contains("[1]");
	}

	@Test
	void q2SepsisAntibioticRetrievesProto002AndCitesIt() {
		String question = "¿Cuándo se administra el primer antibiótico en sepsis?";
		List<ScoredEvidenceFragment> scored = retrieve(question);

		assertThat(scored.get(0).fragment().protocol()).isEqualTo("PROTO-002");
		assertThat(scored.get(0).score()).as("Q2 top score").isGreaterThanOrEqualTo(THRESHOLD);

		RAGResponse response = service((q, evidence) -> Mono
				.just(new LLMCompletion("stub", "stub", jsonAnswer(scored.get(0).fragment()))))
				.answer(new PromptRequest(question, PromptRequest.DEFAULT_TOP_K))
				.block();

		assertThat(response.sources()).extracting(EvidenceFragment::protocol).containsExactly("PROTO-002");
		assertThat(response.citations()).hasSize(1);
		assertThat(response.answer()).contains("[1]");
	}

	@Test
	void q3DiabetesReturnsTheFixedNoticeWithoutCallingTheLlm() {
		String question = "¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?";
		List<ScoredEvidenceFragment> scored = retrieve(question);

		assertThat(scored).allSatisfy(fragment -> assertThat(fragment.score()).as("Q3 score")
				.isLessThan(THRESHOLD));

		AtomicBoolean llmCalled = new AtomicBoolean();
		LLMRouter router = (q, evidence) -> {
			llmCalled.set(true);
			return Mono.error(new AssertionError("no LLM call is allowed below the relevance gate"));
		};

		RAGResponse response = service(router).answer(new PromptRequest(question, PromptRequest.DEFAULT_TOP_K))
				.block();

		assertThat(response).isEqualTo(RAGResponse.noEvidence());
		assertThat(response.answer()).isEqualTo(RAGResponse.NO_PROTOCOL_NOTICE);
		assertThat(response.sources()).isEmpty();
		assertThat(llmCalled).isFalse();
	}

	/**
	 * M-1/M-5 guardrail: the three spec queries keep their outcome when the question goes through the PII-redacting
	 * decorator of the external nodes and the answer through the default grounding post-check. The answers are
	 * markerless paraphrases of the seeded protocols, the shape a real provider may return.
	 */
	@Test
	void specQueriesKeepTheirOutcomeThroughRedactionAndTheGroundingPostCheck() {
		assertThat(throughGuardrails("¿Cuáles son las medidas iniciales ante una crisis hipertensiva?",
				"Repetir la presión arterial tras 5 minutos de reposo, no reducir la PA más del 25% en la primera "
						+ "hora y monitorizar signos de encefalopatía."))
				.extracting(EvidenceFragment::protocol).containsExactly("PROTO-001");
		assertThat(throughGuardrails("¿Cuándo se administra el primer antibiótico en sepsis?",
				"El antibiótico de amplio espectro se administra en la primera hora, después de tomar lactato y "
						+ "hemocultivos."))
				.extracting(EvidenceFragment::protocol).containsExactly("PROTO-002");
		assertThat(throughGuardrails("¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?", "unused"))
				.isEmpty();
	}

	/** @return the cited sources; asserts the question reached the provider unchanged by the redactor */
	private List<EvidenceFragment> throughGuardrails(String question, String markerlessAnswer) {
		AtomicReference<String> sentQuestion = new AtomicReference<>();
		NamedLLMProvider provider = new NamedLLMProvider() {
			@Override
			public String key() {
				return "gemini";
			}

			@Override
			public String model() {
				return "stub";
			}

			@Override
			public Mono<String> generate(String q, List<EvidenceFragment> evidence) {
				sentQuestion.set(q);
				ObjectMapper mapper = new ObjectMapper();
				var root = mapper.createObjectNode();
				root.put("respuesta", markerlessAnswer);
				root.putArray("fuentes").addObject().put("protocolo", evidence.get(0).protocol())
						.put("seccion", evidence.get(0).section());
				return Mono.just(root.toString());
			}
		};
		NamedLLMProvider external = new PiiRedactingLLMProvider(provider);
		LLMRouter router = (q, evidence) -> external.generate(q, evidence)
				.map(text -> new LLMCompletion(external.key(), external.model(), text));

		RAGResponse response = service(router).answer(new PromptRequest(question, PromptRequest.DEFAULT_TOP_K))
				.block();

		if (response.sources().isEmpty()) {
			assertThat(response.answer()).isEqualTo(RAGResponse.NO_PROTOCOL_NOTICE);
		}
		else {
			assertThat(sentQuestion.get()).isEqualTo(question);
			assertThat(response.answer()).isEqualTo(markerlessAnswer);
		}
		return response.sources();
	}

	private List<ScoredEvidenceFragment> retrieve(String question) {
		return store.similaritySearchWithScores(embeddings.embed(question).block(), PromptRequest.DEFAULT_TOP_K)
				.block();
	}

	private static String jsonAnswer(EvidenceFragment fragment) {
		ObjectMapper mapper = new ObjectMapper();
		var root = mapper.createObjectNode();
		root.put("respuesta", "respuesta valida [1]");
		var sources = root.putArray("fuentes");
		sources.addObject().put("protocolo", fragment.protocol()).put("seccion", fragment.section());
		return root.toString();
	}

	private RAGService service(LLMRouter router) {
		return new RAGService(embeddings, store, router, new JacksonGroundedAnswerDecoder(), corpusIndex, THRESHOLD);
	}

}
