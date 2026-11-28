package com.vidacruz.clinical.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.LLMCompletion;
import com.vidacruz.clinical.domain.models.PromptRequest;
import com.vidacruz.clinical.domain.models.RAGResponse;
import com.vidacruz.clinical.domain.models.ScoredEvidenceFragment;
import com.vidacruz.clinical.domain.ports.LLMRouter;
import com.vidacruz.clinical.domain.services.CorpusIndex;
import com.vidacruz.clinical.domain.services.RAGService;
import com.vidacruz.clinical.infrastructure.adapters.GeminiEmbeddingAdapter;
import com.vidacruz.clinical.infrastructure.adapters.PgvectorVectorStoreAdapter;
import com.vidacruz.clinical.infrastructure.config.GeminiEmbeddingProperties;
import com.vidacruz.clinical.infrastructure.db.VectorRepository;
import com.vidacruz.clinical.infrastructure.adapters.JacksonGroundedAnswerDecoder;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Opt-in TST-RAG verification with the real Gemini embedding model. Reads GEMINI_API_KEY from the environment; in CI
 * the workflow_dispatch job injects it from the Actions Secret. Re-embeds the three Flyway seed fragments into a
 * disposable pgvector container, runs five repository-independent retrievals per clinical probe, and writes a JSON
 * evidence artifact. No chat provider, patient data or invented latency/cost value is involved.
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiEmbeddingVerificationTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final int DIMENSIONS = 768;

	private static final int TOP_K = 4;

	private static final int RUNS_PER_PROBE = 5;

	private static final double CALIBRATION_TARGET = 0.65d;

	private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

	private static final String MODEL = "gemini-embedding-2";

	private static final String NEGATIVE_QUESTION = "¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?";

	private static final List<Probe> PROBES = List.of(
			new Probe("PROTO-001", "medidas-iniciales", "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?",
					"PROTO-001"),
			new Probe("PROTO-002", "primer-antibiotico", "¿Cuándo se administra el primer antibiótico en sepsis?",
					"PROTO-002"),
			new Probe("PROTO-003", "leve-4", "Deshidratación pediatrica con perdida de peso del 4 % sin vomito, ¿como se clasifica y rehidrata?",
					"PROTO-003"),
			new Probe("PROTO-003", "moderada-6-5", "Deshidratación pediatrica con perdida de peso del 6,5 % sin vomito, ¿como se clasifica y rehidrata?",
					"PROTO-003"),
			new Probe("PROTO-003", "severa-9", "Deshidratación pediatrica con perdida de peso del 9 %, ¿como se clasifica y rehidrata?",
					"PROTO-003"),
			new Probe("PROTO-003", "alterado-4", "Deshidratación pediatrica con perdida de peso del 4 % y alteracion del estado mental, ¿como se clasifica y rehidrata?",
					"PROTO-003"));

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	private static Scheduler scheduler;

	private static PgvectorVectorStoreAdapter store;

	private static CorpusIndex corpusIndex;

	private static GeminiEmbeddingAdapter embeddings;

	private static final AtomicLong embeddingRequests = new AtomicLong();

	private static final AtomicLong inputCharacters = new AtomicLong();

	@BeforeAll
	static void setUp() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
				POSTGRES.getUsername(), POSTGRES.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		scheduler = Schedulers.newBoundedElastic(2, 10, "gemini-embedding-verification");
		store = new PgvectorVectorStoreAdapter(new VectorRepository(JdbcClient.create(dataSource), DIMENSIONS), scheduler);
		embeddings = new GeminiEmbeddingAdapter(WebClient.builder(),
				new GeminiEmbeddingProperties(BASE_URL, MODEL, System.getenv("GEMINI_API_KEY"), DIMENSIONS, true));
		corpusIndex = new CorpusIndex();
		corpusIndex.refresh(store.protocolSections().block());
		reEmbedSeedCorpus();
	}

	@AfterAll
	static void dispose() {
		scheduler.dispose();
	}

	@Test
	void realEmbeddingsRetrieveEveryProtocolProbeAndRejectTheNegativeQuery() {
		ObjectNode evidence = MAPPER.createObjectNode();
		try {
			evidence.put("generatedAt", Instant.now().toString());
			evidence.put("endpoint", BASE_URL);
			evidence.put("model", MODEL);
			evidence.put("dimensions", DIMENSIONS);
			evidence.put("topK", TOP_K);
			evidence.put("runsPerProbe", RUNS_PER_PROBE);
			evidence.put("calibrationTarget", CALIBRATION_TARGET);
			evidence.put("runtimeThresholdUnchanged", 0.20d);
			ObjectNode cost = evidence.putObject("cost");
			cost.put("basis", "Not measured: no billing API was queried; only request and character counts are recorded");
			cost.put("embeddingRequestsAfterSeeding", 0);

			ArrayNode probes = evidence.putArray("probes");
			double minPositive = Double.POSITIVE_INFINITY;
			for (Probe probe : PROBES) {
				ObjectNode probeNode = probes.addObject();
				probeNode.put("protocol", probe.protocol());
				probeNode.put("probe", probe.name());
				probeNode.put("question", probe.question());
				ArrayNode runs = probeNode.putArray("runs");
				int passedRuns = 0;
				for (int run = 1; run <= RUNS_PER_PROBE; run++) {
					long start = System.nanoTime();
					float[] vector = embedQueryCounted(probe.question());
					List<ScoredEvidenceFragment> scored = store.similaritySearchWithScores(vector, TOP_K).block();
					long latencyMs = (System.nanoTime() - start) / 1_000_000L;
					String topProtocol = scored.isEmpty() ? null : scored.get(0).fragment().protocol();
					double topScore = scored.isEmpty() ? 0d : scored.get(0).score();
					boolean passed = probe.protocol().equals(topProtocol);
					if (passed) {
						passedRuns++;
						minPositive = Math.min(minPositive, topScore);
					}
					ObjectNode runNode = runs.addObject();
					runNode.put("run", run);
					runNode.put("topProtocol", topProtocol);
					runNode.put("topScore", topScore);
					runNode.put("latencyMs", latencyMs);
					runNode.put("passed", passed);
				}
				probeNode.put("passedRuns", passedRuns);
				probeNode.put("passed", passedRuns == RUNS_PER_PROBE);
			}

			ObjectNode answerChecks = evidence.putObject("answerChecks");
			boolean answerChecksPassed = true;
			for (Probe probe : PROBES) {
				if (probe.protocol().equals("PROTO-003")) {
					continue;
				}
				AtomicBoolean llmCalled = new AtomicBoolean();
				RAGService service = service(llmCalled);
				countEmbedding(probe.question());
				RAGResponse response = service.answer(new PromptRequest(probe.question(), TOP_K)).block();
				ObjectNode check = answerChecks.putObject(probe.protocol());
				check.put("hasEvidence", response.hasEvidence());
				ArrayNode sourcesNode = check.putArray("sources");
				response.sources().forEach(source -> sourcesNode.add(source.protocol()));
				check.put("passesCitationGate", response.hasEvidence()
						&& response.sources().stream().anyMatch(source -> probe.protocol().equals(source.protocol()))
						&& !response.citations().isEmpty());
				check.put("llmCalled", llmCalled.get());
				answerChecksPassed &= check.get("passesCitationGate").asBoolean() && llmCalled.get();
			}
			AtomicBoolean negativeLlmCalled = new AtomicBoolean();
			List<ScoredEvidenceFragment> negativeScored = store
					.similaritySearchWithScores(embedQueryCounted(NEGATIVE_QUESTION), TOP_K).block();
			double negativeTopScore = negativeScored.isEmpty() ? 0d : negativeScored.get(0).score();
			countEmbedding(NEGATIVE_QUESTION);
			RAGResponse negative = service(negativeLlmCalled)
					.answer(new PromptRequest(NEGATIVE_QUESTION, TOP_K)).block();
			ObjectNode negativeNode = evidence.putObject("negativeQuery");
			negativeNode.put("question", NEGATIVE_QUESTION);
			negativeNode.put("topScore", negativeTopScore);
			negativeNode.put("noEvidence", !negative.hasEvidence());
			negativeNode.put("answerIsFixedNotice", RAGResponse.NO_PROTOCOL_NOTICE.equals(negative.answer()));
			negativeNode.put("llmCalled", negativeLlmCalled.get());

			evidence.put("minPositiveTopScore", Double.isInfinite(minPositive) ? null : minPositive);
			cost.put("embeddingRequestsAfterSeeding", embeddingRequests.get());
			cost.put("inputCharactersAfterSeeding", inputCharacters.get());

			assertThat(probes).allSatisfy(node -> assertThat(node.get("passed").asBoolean()).isTrue());
			assertThat(answerChecksPassed).as("PROTO-001/PROTO-002 citation gates").isTrue();
			assertThat(negativeNode.get("noEvidence").asBoolean()).isTrue();
			assertThat(negativeNode.get("answerIsFixedNotice").asBoolean()).isTrue();
			assertThat(negativeNode.get("llmCalled").asBoolean()).isFalse();
		} finally {
			evidence.put("finishedAt", Instant.now().toString());
			writeEvidence(evidence);
		}
	}

	private static void reEmbedSeedCorpus() {
		for (Seed seed : seeds()) {
			float[] vector = embedDocumentCounted(seed.content());
			store.save(new EvidenceFragment(UUID.fromString(seed.id()), UUID.fromString(seed.documentId()), 0,
					seed.protocol(), seed.section(), seed.content(), vector)).block();
		}
	}

	private static List<Seed> seeds() {
		return List.of(
				new Seed("a8c651b6-697e-5168-9618-2f0041a2ae7a", "0cff9909-e1f3-57b2-99b5-2480849ba73b",
						"PROTO-001", "Definicion y medidas iniciales",
						"Crisis hipertensiva: PA >= 180/120 mmHg. Emergencia si hay dano a organo diana; urgencia en caso contrario. Medidas iniciales: repetir PA tras 5 minutos de reposo; no reducir PA mas del 25% en la primera hora; monitorizar signos de encefalopatia. Contraindicaciones: nifedipino sublingual; reduccion IV rapida sin monitoreo en cuidados intensivos."),
				new Seed("57e545d7-a125-5518-bfff-d22c8d7a9bde", "d2f7bb54-8cf4-56ba-a038-2c2dfe1e4b1c",
						"PROTO-002", "Identificacion temprana qSOFA y acciones iniciales",
						"Sepsis, identificacion temprana qSOFA: 2 o mas de frecuencia respiratoria >= 22, alteracion del estado mental, PAS <= 100. Acciones iniciales: lactato y hemocultivos antes del antibiotico; antibiotico de amplio espectro en la primera hora; cristaloides 30 ml/kg si hipotension o lactato >= 4."),
				new Seed("5a847ec9-3b15-572c-b39b-acd5c6031577", "db3bcc30-9f8b-5e33-a4d4-9d6cfde7e0d1",
						"PROTO-003", "Clasificacion y rehidratacion pediatrica",
						"Deshidratacion pediatrica. Clasificacion por perdida de peso: leve 3-5%, moderada 5-8%, severa >8% o alteracion del estado mental. Rehidratacion: leve o moderada sin vomito, oral 50 ml/kg en 4 h; severa, bolo IV 20 ml/kg."));
	}

	private static float[] embedQueryCounted(String text) {
		countEmbedding(text);
		return embeddings.embedQuery(text).block();
	}

	private static float[] embedDocumentCounted(String text) {
		countEmbedding(text);
		return embeddings.embedDocument(text).block();
	}

	private static void countEmbedding(String text) {
		embeddingRequests.incrementAndGet();
		inputCharacters.addAndGet(text.length());
	}

	private static RAGService service(AtomicBoolean llmCalled) {
		LLMRouter router = (question, evidence) -> {
			llmCalled.set(true);
			var answerNode = MAPPER.createObjectNode();
			answerNode.put("respuesta", "respuesta valida " + evidence.get(0).content() + " [1]");
			var sources = answerNode.putArray("fuentes");
			sources.addObject().put("protocolo", evidence.get(0).protocol())
					.put("seccion", evidence.get(0).section());
			try {
				return reactor.core.publisher.Mono
						.just(new LLMCompletion("stub", "stub", MAPPER.writeValueAsString(answerNode)));
			}
			catch (JsonProcessingException exception) {
				throw new IllegalStateException("Cannot serialise the stub answer", exception);
			}
		};
		return new RAGService(embeddings, store, router, new JacksonGroundedAnswerDecoder(), corpusIndex, CALIBRATION_TARGET);
	}

	private static void writeEvidence(ObjectNode evidence) {
		try {
			Path directory = Path.of("build", "reports", "gemini-embedding-verification");
			Files.createDirectories(directory);
			Files.writeString(directory.resolve("results.json"),
					MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(evidence), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private record Probe(String protocol, String name, String question, String expectedProtocol) {
	}

	private record Seed(String id, String documentId, String protocol, String section, String content) {
	}

}
