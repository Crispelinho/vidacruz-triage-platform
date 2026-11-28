package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.sun.net.httpserver.HttpServer;
import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.LLMCompletion;
import com.vidacruz.clinical.domain.models.PromptRequest;
import com.vidacruz.clinical.domain.models.ProtocolSection;
import com.vidacruz.clinical.domain.models.RAGResponse;
import com.vidacruz.clinical.domain.models.ScoredEvidenceFragment;
import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.LLMRouter;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;
import com.vidacruz.clinical.domain.ports.VectorStore;
import com.vidacruz.clinical.domain.services.CorpusIndex;
import com.vidacruz.clinical.domain.services.RAGService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * OD-16: {@code contexto_paciente} is validated by {@link PromptRequest} but must never reach a cloud LLM payload. The
 * RAG use case runs through the real Spring AI streaming adapters against a local stub, so the assertions are on the
 * JSON body and auth headers actually sent over the wire, plus the HTTP failure mapping. No real provider, API key or
 * clinical data is used.
 */
class CloudPromptPrivacyTest {

	private static final String QUESTION = "medidas ante crisis hipertensiva";

	private static final String PATIENT_CONTEXT = "PACIENTE-CONFIDENCIAL-42";

	private static final String EVIDENCE_TEXT = "PROTO-001: contenido del protocolo";

	private static final float[] QUERY_EMBEDDING = { 1f };

	private static final String ANSWER = "respuesta [1]";

	/** G-04 JSON answer the stubbed provider returns; the test asserts only the wire payload, not generation. */
	private static final String GROUNDED_JSON = "{\"respuesta\":\"" + ANSWER
			+ "\",\"fuentes\":[{\"protocolo\":\"PROTO-001\",\"seccion\":\"Medidas\"}]}";

	private HttpServer server;

	private final AtomicReference<String> capturedBody = new AtomicReference<>();

	private final AtomicReference<String> capturedPath = new AtomicReference<>();

	private final AtomicReference<String> capturedApiKey = new AtomicReference<>();

	private final AtomicReference<String> capturedAuthorization = new AtomicReference<>();

	private int responseStatus = 200;

	@BeforeEach
	void startStubServer() throws IOException {
		capturedBody.set(null);
		capturedPath.set(null);
		capturedApiKey.set(null);
		capturedAuthorization.set(null);
		responseStatus = 200;
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			capturedPath.set(exchange.getRequestURI().toString());
			capturedApiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
			capturedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			String escaped = GROUNDED_JSON.replace("\\", "\\\\").replace("\"", "\\\"");
			String payload = responseStatus == 200
					? (exchange.getRequestURI().getPath().toLowerCase().contains("generatecontent")
							? "data: {\"modelVersion\":\"gemini-3.5-flash-lite\",\"candidates\":[{\"content\":{\"parts\":[{\"text\":\""
									+ escaped + "\"}],\"role\":\"model\"},\"finishReason\":\"STOP\"}]}\n\n"
							: "data: {\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"" + escaped
									+ "\"},\"finish_reason\":null}]}\n\ndata: [DONE]\n\n")
					: "{\"error\":\"overloaded\"}";
			byte[] response = payload.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type",
					responseStatus == 200 ? "text/event-stream" : "application/json");
			exchange.sendResponseHeaders(responseStatus, response.length);
			try (var output = exchange.getResponseBody()) {
				output.write(response);
			}
		});
		server.start();
	}

	@AfterEach
	void stopStubServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void geminiPayloadContainsOnlyTheQuestionAndRetrievedEvidence() {
		RAGResponse response = answerThrough(geminiNode());

		assertThat(response.answer()).isEqualTo(ANSWER);
		assertThat(capturedBody.get()).isNotNull()
				.contains(QUESTION)
				.contains(EVIDENCE_TEXT)
				.doesNotContain(PATIENT_CONTEXT);
		assertThat(capturedPath.get()).contains(":streamGenerateContent");
		assertThat(capturedApiKey.get()).isEqualTo("test-key");
	}

	@Test
	void opencodePayloadContainsOnlyTheQuestionAndRetrievedEvidence() {
		RAGResponse response = answerThrough(opencodeNode());

		assertThat(response.answer()).isEqualTo(ANSWER);
		assertThat(capturedBody.get()).isNotNull()
				.contains(QUESTION)
				.contains(EVIDENCE_TEXT)
				.doesNotContain(PATIENT_CONTEXT);
		assertThat(capturedPath.get()).endsWith("/v1/chat/completions");
		assertThat(capturedAuthorization.get()).isEqualTo("Bearer test-key");
	}

	@Test
	void httpFailuresMapToTheSharedFailoverTrigger() {
		responseStatus = 503;

		assertThatThrownBy(() -> answerThrough(geminiNode()))
				.isInstanceOf(LLMUnavailableException.class)
				.hasMessageContaining("gemini/gemini-3.5-flash-lite");
		assertThatThrownBy(() -> answerThrough(opencodeNode()))
				.isInstanceOf(LLMUnavailableException.class)
				.hasMessageContaining("opencode/glm-5.3");
	}

	private NamedLLMProvider geminiNode() {
		Client client = Client.builder()
				.apiKey("test-key")
				.httpOptions(HttpOptions.builder().baseUrl(baseUrl()).build())
				.build();
		ChatModel model = GoogleGenAiChatModel.builder()
				.genAiClient(client)
				.defaultOptions(GoogleGenAiChatOptions.builder().model("gemini-3.5-flash-lite").build())
				.build();
		// Google GenAI exposes a blocking Iterable; production wires boundedElastic for this node (TST-RXN).
		return new SpringAIChatAdapter("gemini", ChatClient.create(model), "gemini-3.5-flash-lite",
				Schedulers.boundedElastic());
	}

	private NamedLLMProvider opencodeNode() {
		OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl() + "/v1").apiKey("test-key").build();
		ChatModel model = OpenAiChatModel.builder()
				.openAiApi(api)
				.defaultOptions(OpenAiChatOptions.builder().model("glm-5.3").build())
				.build();
		return new SpringAIChatAdapter("opencode", ChatClient.create(model), "glm-5.3");
	}

	private String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	/** Real service, real Spring AI adapters, stubbed retrieval: only the patient context is not forwarded. */
	private RAGResponse answerThrough(NamedLLMProvider node) {
		EmbeddingProvider embeddings = mock(EmbeddingProvider.class);
		VectorStore vectorStore = mock(VectorStore.class);
		EvidenceFragment fragment = EvidenceFragment.create(UUID.randomUUID(), 0, "PROTO-001", "Medidas",
				EVIDENCE_TEXT, QUERY_EMBEDDING);
		when(embeddings.embed(QUESTION)).thenReturn(Mono.just(QUERY_EMBEDDING));
		when(vectorStore.similaritySearchWithScores(QUERY_EMBEDDING, 1))
				.thenReturn(Mono.just(List.of(new ScoredEvidenceFragment(fragment, 1.0d))));

		LLMRouter router = (question, evidence) -> node.generate(question, evidence)
				.map(answer -> new LLMCompletion(node.key(), node.model(), answer));
		CorpusIndex corpusIndex = new CorpusIndex();
		corpusIndex.add(new ProtocolSection("PROTO-001", "Medidas"));
		RAGService service = new RAGService(embeddings, vectorStore, router, new JacksonGroundedAnswerDecoder(), corpusIndex,
				RAGService.DEFAULT_RELEVANCE_THRESHOLD);
		PromptRequest request = new PromptRequest(QUESTION, 1, PATIENT_CONTEXT, null);
		// TST-RXN: the whole RAG call runs on a non-blocking reactor thread; BlockHound fails on any blocking SDK read.
		return service.answer(request).subscribeOn(Schedulers.parallel()).block();
	}

}
