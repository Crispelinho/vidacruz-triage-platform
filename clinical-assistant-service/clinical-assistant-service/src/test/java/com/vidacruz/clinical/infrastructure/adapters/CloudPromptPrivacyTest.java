package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.sun.net.httpserver.HttpServer;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.LLMCompletion;
import com.vidacruz.clinical.domain.models.PromptRequest;
import com.vidacruz.clinical.domain.models.RAGResponse;
import com.vidacruz.clinical.domain.models.ScoredEvidenceFragment;
import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.LLMRouter;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;
import com.vidacruz.clinical.domain.ports.VectorStore;
import com.vidacruz.clinical.domain.services.RAGService;
import com.vidacruz.clinical.infrastructure.config.GeminiProperties;
import com.vidacruz.clinical.infrastructure.config.OpenCodeProperties;

import reactor.core.publisher.Mono;

/**
 * OD-16: {@code contexto_paciente} is validated by {@link PromptRequest} but must never reach a cloud LLM payload. The
 * RAG use case runs against a real HTTP adapter and a local stub, so the assertion is on the JSON body actually sent
 * over the wire, not on a mocked adapter. No real provider, API key or clinical data is used.
 */
class CloudPromptPrivacyTest {

	private static final String QUESTION = "medidas ante crisis hipertensiva";

	private static final String PATIENT_CONTEXT = "PACIENTE-CONFIDENCIAL-42";

	private static final String EVIDENCE_TEXT = "PROTO-001: contenido del protocolo";

	private static final float[] QUERY_EMBEDDING = { 1f };

	private static final String ANSWER = "respuesta [1]";

	private HttpServer server;

	private final AtomicReference<String> capturedBody = new AtomicReference<>();

	@BeforeEach
	void startStubServer() throws IOException {
		capturedBody.set(null);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			String path = exchange.getRequestURI().getPath();
			String responseJson = path.endsWith(":generateContent")
					? "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + ANSWER + "\"}]}}]}"
					: "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + ANSWER + "\"}}]}";
			byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, response.length);
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
		GeminiProperties properties = new GeminiProperties(baseUrl(), "gemini-3.5-flash-lite", "test-key");
		NamedLLMProvider node = new GeminiHttpLLMAdapter(WebClient.builder(), properties);

		RAGResponse response = answerThrough(node);

		assertThat(response.answer()).isEqualTo(ANSWER);
		assertThat(capturedBody.get()).isNotNull()
				.contains(QUESTION)
				.contains(EVIDENCE_TEXT)
				.doesNotContain(PATIENT_CONTEXT);
	}

	@Test
	void opencodePayloadContainsOnlyTheQuestionAndRetrievedEvidence() {
		OpenCodeProperties properties = new OpenCodeProperties(baseUrl() + "/v1/chat/completions", "glm-5.3",
				"test-key");
		NamedLLMProvider node = new OpenCodeHttpLLMAdapter(WebClient.builder(), properties);

		RAGResponse response = answerThrough(node);

		assertThat(response.answer()).isEqualTo(ANSWER);
		assertThat(capturedBody.get()).isNotNull()
				.contains(QUESTION)
				.contains(EVIDENCE_TEXT)
				.doesNotContain(PATIENT_CONTEXT);
	}

	private String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	/** Real service, real adapter, stubbed retrieval: only the patient context is deliberately not forwarded. */
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
		RAGService service = new RAGService(embeddings, vectorStore, router);
		PromptRequest request = new PromptRequest(QUESTION, 1, PATIENT_CONTEXT, null);

		return service.answer(request).block();
	}

}
