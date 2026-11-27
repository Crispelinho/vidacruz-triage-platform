package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.sun.net.httpserver.HttpServer;
import com.vidacruz.clinical.infrastructure.config.GeminiEmbeddingProperties;

/**
 * Wire-level contract for the real Gemini embeddings adapter against a local stub. Asserts the endpoint, the API key
 * header, the snake_case body field Google's REST API requires and the retrieval task prefixes. No real key is used.
 */
class GeminiEmbeddingAdapterWireTest {

	private static final int DIMENSIONS = 768;

	private static final String MODEL = "gemini-embedding-2";

	private HttpServer server;

	private final AtomicReference<String> path = new AtomicReference<>();

	private final AtomicReference<String> apiKey = new AtomicReference<>();

	private final AtomicReference<String> body = new AtomicReference<>();

	@BeforeEach
	void startStub() throws IOException {
		path.set(null);
		apiKey.set(null);
		body.set(null);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			path.set(exchange.getRequestURI().getPath());
			apiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			String values = IntStream.range(0, DIMENSIONS).mapToObj(i -> "0.1").collect(Collectors.joining(","));
			byte[] response = ("{\"embedding\":{\"values\":[" + values + "]}}").getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stopStub() {
		server.stop(0);
	}

	@Test
	void documentEmbeddingUsesDocumentPrefixAndRequiredJsonField() {
		float[] vector = adapter().embedDocument("crisis hipertensiva").block();

		assertThat(vector).hasSize(DIMENSIONS);
		assertThat(path.get()).isEqualTo("/" + MODEL + ":embedContent");
		assertThat(apiKey.get()).isEqualTo("wire-test-key");
		assertThat(body.get()).contains("\"output_dimensionality\":" + DIMENSIONS)
				.contains("title: none | text: crisis hipertensiva")
				.doesNotContain("task_type");
	}

	@Test
	void queryEmbeddingUsesQuestionAnsweringPrefix() {
		adapter().embedQuery("¿medidas iniciales?").block();

		assertThat(body.get()).contains("task: question answering | query: ¿medidas iniciales?");
	}

	@Test
	void plainEmbedKeepsTheTextUnprefixed() {
		adapter().embed("texto sin prefijo").block();

		assertThat(body.get()).contains("\"text\":\"texto sin prefijo\"").doesNotContain("task:");
	}

	private GeminiEmbeddingAdapter adapter() {
		GeminiEmbeddingProperties properties = new GeminiEmbeddingProperties(
				"http://127.0.0.1:" + server.getAddress().getPort(), MODEL, "wire-test-key", DIMENSIONS, true);
		return new GeminiEmbeddingAdapter(WebClient.builder(), properties);
	}

}
