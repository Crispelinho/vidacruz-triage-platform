package com.vidacruz.clinical.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.services.TextChunker;
import com.vidacruz.clinicalassistantservice.ClinicalAssistantServiceApplication;

import reactor.core.publisher.Mono;

/**
 * RF-04A: corpus ingestion and RN-05 gated retrieval, end to end against a real pgvector.
 *
 * <p>The endpoint and persistence contract exercised here is collected in this class:
 * <ul>
 * <li>{@code POST /clinical/corpus/ingest} with {@code {"title", "content"}} answers 200
 * {@code {"documentId", "chunksCount"}}. Re-ingesting the same title and content returns the same documentId.</li>
 * <li>{@code GET /clinical/corpus/retrieve?query=..&threshold=..} answers 200 with a JSON array (never null) of
 * {@code {"text", "similarity", "source"}}, where {@code source} is the document title, keeping only chunks whose
 * cosine similarity is {@code >= threshold} (RN-05).</li>
 * <li>Chunks live in table {@code evidence_fragments(document_id uuid, chunk_index int, content text,
 * embedding vector(768))}.</li>
 * </ul>
 *
 * <p>Embeddings are hardcoded (see {@link MockEmbeddingProvider}), so similarities are exact by construction and no
 * real model is exercised. Every test uses its own tag in titles and content, so documents never collide.
 */
@SpringBootTest(classes = ClinicalAssistantServiceApplication.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "adapters.kafka.patient-events.enabled=false")
@Import(CorpusIngestionIntegrationTest.Config.class)
@AutoConfigureWebTestClient
class CorpusIngestionIntegrationTest {

	private static final String INGEST_URI = "/clinical/corpus/ingest";

	private static final String SEARCH_URI = "/clinical/corpus/retrieve";

	private static final int EMBEDDING_DIMENSIONS = 768;

	private static final double HEART_FAILURE_SIMILARITY = MockEmbeddingProvider.HEART_FAILURE_COSINE;

	private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
	};

	private static final ParameterizedTypeReference<List<Map<String, Object>>> MAP_LIST = new ParameterizedTypeReference<>() {
	};

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private DatabaseClient db;

	// Spring AI's pgvector auto-configuration requires an EmbeddingModel bean; the scaffold configures none.
	@MockitoBean
	EmbeddingModel embeddingModel;

	@BeforeEach
	void cleanChunks() {
		db.sql("TRUNCATE TABLE evidence_fragments").fetch().rowsUpdated().block();
	}

	// 1. Happy path ------------------------------------------------------------------------------------------------

	@Test
	void ingestsAClinicalDocumentAndStoresItsChunksWithEmbeddings() {
		String tag = newTag();
		String content = body(Topic.CARDIO, tag, 1000);

		Map<String, Object> ingested = ingest("Heart failure guideline " + tag, content);

		String documentId = documentIdOf(ingested);
		int chunksCount = chunksCountOf(ingested);
		assertThat(UUID.fromString(documentId)).isNotNull();
		assertThat(chunksCount).as("a 1000-word document must be split").isGreaterThan(1);

		assertThat(countChunks(documentId)).as("persisted chunks").isEqualTo(chunksCount);
		assertThat(countChunksWithEmbedding(documentId)).as("chunks with an embedding").isEqualTo(chunksCount);
		assertThat(distinctEmbeddingDimensions(documentId)).as("embedding dimensions")
				.containsExactly(EMBEDDING_DIMENSIONS);
	}

	// 2. RN-05 gate ------------------------------------------------------------------------------------------------

	@Test
	void retrievalKeepsOnlyChunksAtOrAboveTheThreshold() {
		String tag = newTag();
		String cardiologyTitle = "Cardiology " + tag;
		ingest(cardiologyTitle, body(Topic.CARDIO, tag, 300));
		ingest("Neurology " + tag, body(Topic.NEURO, tag, 300));
		ingest("Orthopedics " + tag, body(Topic.ORTHO, tag, 300));

		List<Map<String, Object>> atDefaultGate = search("heart failure", 0.75);
		List<Map<String, Object>> atStrictGate = search("heart failure", 0.95);

		assertThat(atDefaultGate).as("chunks at threshold 0.75").isNotEmpty();
		assertThat(atDefaultGate).allSatisfy(chunk -> {
			assertThat(similarityOf(chunk)).isGreaterThanOrEqualTo(0.75);
			assertThat(sourceOf(chunk)).isEqualTo(cardiologyTitle);
		});
		assertThat(atDefaultGate).extracting(CorpusIngestionIntegrationTest::similarityOf)
				.allSatisfy(similarity -> assertThat(similarity).isCloseTo(HEART_FAILURE_SIMILARITY,
						org.assertj.core.data.Offset.offset(0.01)));

		assertThat(atStrictGate).as("a stricter threshold is never more permissive")
				.hasSizeLessThan(atDefaultGate.size());
		assertThat(atStrictGate).allSatisfy(chunk -> assertThat(similarityOf(chunk)).isGreaterThanOrEqualTo(0.95));
	}

	// 3. No hallucination ------------------------------------------------------------------------------------------

	@Test
	void anUnrelatedQueryReturnsAnEmptyListNotNullNorAnError() {
		String tag = newTag();
		ingest("Cardiology " + tag, body(Topic.CARDIO, tag, 300));
		ingest("Neurology " + tag, body(Topic.NEURO, tag, 300));
		ingest("Orthopedics " + tag, body(Topic.ORTHO, tag, 300));

		authenticated().get()
				.uri(uri -> uri.path(SEARCH_URI).queryParam("query", "quantum physics").queryParam("threshold", 0.75)
						.build())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$").isArray()
				.jsonPath("$").isEmpty();
	}

	// 4. Idempotency -----------------------------------------------------------------------------------------------

	@Test
	void ingestingTheSameDocumentTwiceDoesNotDuplicateChunks() {
		String tag = newTag();
		String title = "Heart failure guideline " + tag;
		String content = body(Topic.CARDIO, tag, 1000);

		Map<String, Object> first = ingest(title, content);
		Map<String, Object> second = ingest(title, content);

		String documentId = documentIdOf(first);
		int chunksCount = chunksCountOf(first);
		assertThat(documentIdOf(second)).as("same document, same id").isEqualTo(documentId);
		assertThat(chunksCountOf(second)).isEqualTo(chunksCount);

		assertThat(countChunks(documentId)).as("no duplicated chunks").isEqualTo(chunksCount);
		assertThat(countChunksContaining(tag)).as("all chunks of this content").isEqualTo(chunksCount);
		assertThat(countChunksContainingWithOtherDocument(tag, documentId)).as("chunks pointing at another document")
				.isZero();
	}

	// 5. Chunk accuracy --------------------------------------------------------------------------------------------

	@Test
	void chunksCoverTheWholeDocumentStayWithinTheTokenBudgetAndOverlapAtMostHalf() {
		String tag = newTag();
		String content = threeSectionDocument(tag);

		Map<String, Object> ingested = ingest("Three section protocol " + tag, content);

		String documentId = documentIdOf(ingested);
		List<String> chunks = chunkContents(documentId);
		assertThat(chunks).hasSize(chunksCountOf(ingested));
		assertThat(chunkIndexes(documentId)).as("chunk positions are contiguous")
				.containsExactlyElementsOf(java.util.stream.IntStream.range(0, chunks.size()).boxed().toList());

		// TextChunker counts whitespace-delimited words as tokens; each chunk fits the configured budget.
		assertThat(chunks).allSatisfy(chunk -> assertThat(words(chunk).length)
				.isLessThanOrEqualTo(TextChunker.DEFAULT_CHUNK_TOKENS));

		// Coverage: every word of the source is in at least one chunk, including each section's first and last marker.
		Set<String> covered = new HashSet<>();
		chunks.forEach(chunk -> covered.addAll(Arrays.asList(words(chunk))));
		assertThat(covered).containsAll(Arrays.asList(words(content)));
		for (String section : List.of("ONE", "TWO", "THREE")) {
			assertThat(chunks).anyMatch(chunk -> chunk.contains("SECTION_" + section + "_START"));
			assertThat(chunks).anyMatch(chunk -> chunk.contains("SECTION_" + section + "_END"));
		}

		// Overlap between neighbours never exceeds half of the earlier chunk.
		for (int i = 0; i < chunks.size() - 1; i++) {
			int overlap = overlapInWords(chunks.get(i), chunks.get(i + 1));
			assertThat(overlap).as("overlap between chunk %d and %d", i, i + 1)
					.isLessThanOrEqualTo(words(chunks.get(i)).length / 2);
		}
	}

	// 6. Multiple documents ----------------------------------------------------------------------------------------

	@Test
	void retrievalReportsSourceTitlesAndOnlyReturnsCardiologySources() {
		String tag = newTag();
		Set<String> cardiologyTitles = new HashSet<>();
		for (String title : List.of("Heart failure guideline", "Cardiac rehabilitation", "Ventricular arrhythmia")) {
			String fullTitle = title + " " + tag;
			cardiologyTitles.add(fullTitle);
			ingest(fullTitle, body(Topic.CARDIO, tag + title.length(), 300));
		}
		ingest("Neurology " + tag, body(Topic.NEURO, tag, 300));
		ingest("Orthopedics " + tag, body(Topic.ORTHO, tag, 300));

		List<Map<String, Object>> results = search("heart", 0.75);

		assertThat(results).isNotEmpty();
		assertThat(results).allSatisfy(chunk -> {
			assertThat(sourceOf(chunk)).as("only cardiology sources").isIn(cardiologyTitles);
			assertThat((String) chunk.get("text")).isNotBlank();
		});
	}

	// 7. Edge cases ------------------------------------------------------------------------------------------------

	@Test
	void edgeCasesAreHandledWithoutErrors() {
		String tag = newTag();
		ingest("Cardiology " + tag, body(Topic.CARDIO, tag, 300));
		ingest("Neurology " + tag, body(Topic.NEURO, tag, 300));

		SoftAssertions softly = new SoftAssertions();

		// Empty document: either rejected, or accepted with nothing to index.
		EntityExchangeResult<Map<String, Object>> empty = authenticated().post().uri(INGEST_URI)
				.bodyValue(Map.of("title", "Empty " + tag, "content", ""))
				.exchange()
				.expectBody(MAP).returnResult();
		if (empty.getStatus().is2xxSuccessful()) {
			softly.assertThat(empty.getResponseBody()).as("empty document response").isNotNull();
			softly.assertThat(chunksCountOf(empty.getResponseBody())).as("empty document yields no chunks").isZero();
		}
		else {
			softly.assertThat(empty.getStatus().is4xxClientError()).as("empty document status").isTrue();
		}

		// Single-word query still matches its topic.
		softly.assertThat(search("heart", 0.75)).as("single-word query").isNotEmpty();

		// Very low threshold is permissive for related chunks, but unrelated ones (cosine 0) stay out.
		List<Map<String, Object>> permissive = search("heart failure", 0.1);
		softly.assertThat(permissive).as("threshold 0.1").isNotEmpty()
				.allSatisfy(chunk -> softly.assertThat(similarityOf(chunk)).isGreaterThanOrEqualTo(0.1));

		// Very high threshold filters out everything that is not a near-exact match.
		softly.assertThat(search("heart failure", 0.99)).as("threshold 0.99").isEmpty();

		softly.assertAll();
	}

	// Test infrastructure ------------------------------------------------------------------------------------------

	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		/** Takes precedence over the fixture hash embedding wired by RAGConfiguration. */
		@Bean
		@Primary
		EmbeddingProvider mockEmbeddingProvider() {
			return new MockEmbeddingProvider();
		}

		/** Authentication is not under test here. */
		@Bean
		@Order(Ordered.HIGHEST_PRECEDENCE)
		SecurityWebFilterChain permitAllSecurity(ServerHttpSecurity http) {
			return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
					.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
					.build();
		}

	}

	/**
	 * Hardcoded, semantics-free embeddings so cosine similarities are exact. Each topic owns an axis; text is mapped
	 * to its dominant topic's axis. Known queries are tilted by a fixed angle toward a noise axis, which makes their
	 * similarity with an on-topic chunk exactly the tilt (and 0 with any other topic).
	 */
	static final class MockEmbeddingProvider implements EmbeddingProvider {

		static final double HEART_FAILURE_COSINE = 0.80;

		private static final int NOISE_AXIS = 10;

		private static final Map<String, float[]> KNOWN_QUERIES = Map.of(
				"heart failure", tilted(Topic.CARDIO, HEART_FAILURE_COSINE));

		@Override
		public Mono<float[]> embed(String text) {
			return Mono.fromSupplier(() -> {
				float[] known = KNOWN_QUERIES.get(text.trim().toLowerCase(Locale.ROOT));
				return known != null ? known : unit(Topic.dominantOf(text).axis);
			});
		}

		private static float[] unit(int axis) {
			float[] vector = new float[EMBEDDING_DIMENSIONS];
			vector[axis] = 1f;
			return vector;
		}

		private static float[] tilted(Topic topic, double cosine) {
			float[] vector = new float[EMBEDDING_DIMENSIONS];
			vector[topic.axis] = (float) cosine;
			vector[NOISE_AXIS] = (float) Math.sqrt(1 - cosine * cosine);
			return vector;
		}

	}

	private enum Topic {

		CARDIO(0, new String[] { "heart", "cardiac", "cardio", "ventricular" },
				new String[] {
						"The patient with heart failure shows cardiac dyspnea and ventricular dysfunction",
						"Cardiac monitoring tracks ventricular response and heart rhythm during treatment",
						"Heart failure staging guides diuretic dosing and cardiac follow up",
						"Ventricular ejection fraction is reviewed at each cardiac visit" }),

		NEURO(1, new String[] { "brain", "neuro", "stroke", "seizure", "cerebral" },
				new String[] {
						"The patient with stroke shows cerebral symptoms and brain edema",
						"Neurological assessment tracks seizure activity and cerebral perfusion",
						"Stroke protocol requires brain imaging before any treatment decision",
						"Seizure control is reviewed at each neurological visit" }),

		ORTHO(2, new String[] { "bone", "fracture", "orthopedic", "joint", "skeletal" },
				new String[] {
						"The patient with a fracture shows bone pain and joint swelling",
						"Orthopedic assessment tracks skeletal alignment and joint mobility",
						"Fracture management requires immobilization and bone healing review",
						"Joint range of motion is reviewed at each orthopedic visit" }),

		/** Anything that matches no clinical topic. */
		OTHER(3, new String[0], new String[0]);

		final int axis;

		final String[] keywords;

		final String[] sentences;

		Topic(int axis, String[] keywords, String[] sentences) {
			this.axis = axis;
			this.keywords = keywords;
			this.sentences = sentences;
		}

		static Topic dominantOf(String text) {
			String lower = text.toLowerCase(Locale.ROOT);
			Topic best = OTHER;
			int bestScore = 0;
			for (Topic topic : values()) {
				int score = 0;
				for (String keyword : topic.keywords) {
					for (int at = lower.indexOf(keyword); at >= 0; at = lower.indexOf(keyword, at + 1)) {
						score++;
					}
				}
				if (score > bestScore) {
					best = topic;
					bestScore = score;
				}
			}
			return best;
		}

	}

	// Fixtures -----------------------------------------------------------------------------------------------------

	private static String newTag() {
		return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}

	/** At least {@code minWords} words of on-topic text. Every sentence carries the tag and a running number. */
	private static String body(Topic topic, String tag, int minWords) {
		StringBuilder text = new StringBuilder();
		int words = 0;
		for (int i = 0; words < minWords; i++) {
			String sentence = "Record " + tag + "-" + i + ": " + topic.sentences[i % topic.sentences.length] + ". ";
			text.append(sentence);
			words += words(sentence).length;
		}
		return text.toString().trim();
	}

	private static String threeSectionDocument(String tag) {
		StringBuilder text = new StringBuilder();
		for (String section : List.of("ONE", "TWO", "THREE")) {
			text.append("## Section ").append(section).append("\nSECTION_").append(section).append("_START ")
					.append(body(Topic.CARDIO, tag + section.length() + section.charAt(0), 400))
					.append(" SECTION_").append(section).append("_END\n\n");
		}
		return text.toString().trim();
	}

	// HTTP helpers -------------------------------------------------------------------------------------------------

	/** The API-key WebFilter runs before the permit-all chain installed by Config. */
	private WebTestClient authenticated() {
		return webTestClient.mutate().defaultHeader("X-API-Key", "test-assistant-key").build();
	}

	private Map<String, Object> ingest(String title, String content) {
		Map<String, Object> response = authenticated().post().uri(INGEST_URI)
				.bodyValue(Map.of("title", title, "content", content))
				.exchange()
				.expectStatus().isOk()
				.expectBody(MAP)
				.returnResult().getResponseBody();
		assertThat(response).isNotNull();
		return response;
	}

	private List<Map<String, Object>> search(String query, double threshold) {
		List<Map<String, Object>> response = authenticated().get()
				.uri(uri -> uri.path(SEARCH_URI).queryParam("query", query).queryParam("threshold", threshold).build())
				.exchange()
				.expectStatus().isOk()
				.expectBody(MAP_LIST)
				.returnResult().getResponseBody();
		assertThat(response).as("the response body is a list, never null").isNotNull();
		return response;
	}

	private static String documentIdOf(Map<String, Object> ingested) {
		assertThat(ingested.get("documentId")).as("documentId").isInstanceOf(String.class);
		return (String) ingested.get("documentId");
	}

	private static int chunksCountOf(Map<String, Object> ingested) {
		assertThat(ingested.get("chunksCount")).as("chunksCount").isInstanceOf(Number.class);
		return ((Number) ingested.get("chunksCount")).intValue();
	}

	private static double similarityOf(Map<String, Object> chunk) {
		assertThat(chunk.get("similarity")).as("similarity score").isInstanceOf(Number.class);
		return ((Number) chunk.get("similarity")).doubleValue();
	}

	private static String sourceOf(Map<String, Object> chunk) {
		assertThat(chunk.get("source")).as("source title").isInstanceOf(String.class);
		return (String) chunk.get("source");
	}

	// Persistence helpers (R2DBC) ----------------------------------------------------------------------------------

	private long countChunks(String documentId) {
		return db.sql("SELECT count(*) AS n FROM evidence_fragments WHERE document_id = CAST(:id AS uuid)")
				.bind("id", documentId)
				.map((row, metadata) -> row.get("n", Long.class))
				.one().block();
	}

	private long countChunksWithEmbedding(String documentId) {
		return db.sql("SELECT count(*) AS n FROM evidence_fragments WHERE document_id = CAST(:id AS uuid) "
				+ "AND embedding IS NOT NULL")
				.bind("id", documentId)
				.map((row, metadata) -> row.get("n", Long.class))
				.one().block();
	}

	private List<Integer> distinctEmbeddingDimensions(String documentId) {
		return db.sql("SELECT DISTINCT vector_dims(embedding) AS dims FROM evidence_fragments "
				+ "WHERE document_id = CAST(:id AS uuid)")
				.bind("id", documentId)
				.map((row, metadata) -> row.get("dims", Integer.class))
				.all().collectList().block();
	}

	private long countChunksContaining(String tag) {
		return db.sql("SELECT count(*) AS n FROM evidence_fragments WHERE content LIKE :pattern")
				.bind("pattern", "%" + tag + "%")
				.map((row, metadata) -> row.get("n", Long.class))
				.one().block();
	}

	private long countChunksContainingWithOtherDocument(String tag, String documentId) {
		return db.sql("SELECT count(*) AS n FROM evidence_fragments WHERE content LIKE :pattern "
				+ "AND document_id <> CAST(:id AS uuid)")
				.bind("pattern", "%" + tag + "%")
				.bind("id", documentId)
				.map((row, metadata) -> row.get("n", Long.class))
				.one().block();
	}

	private List<String> chunkContents(String documentId) {
		return db.sql("SELECT content FROM evidence_fragments WHERE document_id = CAST(:id AS uuid) "
				+ "ORDER BY chunk_index")
				.bind("id", documentId)
				.map((row, metadata) -> row.get("content", String.class))
				.all().collectList().block();
	}

	private List<Integer> chunkIndexes(String documentId) {
		return db.sql("SELECT chunk_index FROM evidence_fragments WHERE document_id = CAST(:id AS uuid) "
				+ "ORDER BY chunk_index")
				.bind("id", documentId)
				.map((row, metadata) -> row.get("chunk_index", Integer.class))
				.all().collectList().block();
	}

	// Text helpers -------------------------------------------------------------------------------------------------

	private static String[] words(String text) {
		return text.trim().split("\\s+");
	}

	/** Longest run of words that ends {@code earlier} and starts {@code later}. */
	private static int overlapInWords(String earlier, String later) {
		String[] a = words(earlier);
		String[] b = words(later);
		for (int k = Math.min(a.length, b.length); k > 0; k--) {
			if (Arrays.equals(Arrays.copyOfRange(a, a.length - k, a.length), Arrays.copyOfRange(b, 0, k))) {
				return k;
			}
		}
		return 0;
	}

}
