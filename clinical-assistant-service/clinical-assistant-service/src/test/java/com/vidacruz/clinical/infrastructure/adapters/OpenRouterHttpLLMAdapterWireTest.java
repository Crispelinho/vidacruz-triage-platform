package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.infrastructure.config.OpenRouterProperties;

/**
 * Wire-level contract for the optional OpenRouter node against a local stub. No real key or provider is used. It checks
 * the endpoint, the Bearer header, the always-on privacy controls and the grounded prompt, and the failure mapping.
 */
class OpenRouterHttpLLMAdapterWireTest {

	private static final String MODEL = "nvidia/nemotron-3.5-lightning:free";

	private static final String GROUNDED_ANSWER = """
			{"respuesta":"respuesta valida [1]","fuentes":[{"protocolo":"PROTO-001","seccion":"Medidas"}]}
			""";

	private HttpServer server;

	private final AtomicInteger status = new AtomicInteger(200);

	private final AtomicReference<String> path = new AtomicReference<>();

	private final AtomicReference<String> authorization = new AtomicReference<>();

	private final AtomicReference<String> body = new AtomicReference<>();

	@BeforeEach
	void startStub() throws IOException {
		status.set(200);
		path.set(null);
		authorization.set(null);
		body.set(null);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			path.set(exchange.getRequestURI().getPath());
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] response = responseBody(status.get());
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(status.get(), response.length);
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
	void sendsPrivacyControlsAndParsesTheGroundedAnswer() {
		String answer = adapter("wire-test-key").generate("crisis hipertensiva", List.of(fragment())).block();

		assertThat(answer).contains("respuesta valida [1]");
		assertThat(path.get()).isEqualTo("/chat/completions").doesNotContain("wire-test-key");
		assertThat(authorization.get()).isEqualTo("Bearer wire-test-key");
		assertThat(body.get()).contains("\"model\":\"" + MODEL + "\"")
				.contains("\"data_collection\":\"deny\"")
				.contains("\"max_price\":{\"prompt\":0,\"completion\":0}")
				// M-1: system message first, then the delimited question and evidence as the user message.
				.contains("{\"role\":\"system\",\"content\":\"You are a clinical protocol assistant")
				.contains("<pregunta>crisis hipertensiva</pregunta>")
				.contains("protocolo=\\\"PROTO-001\\\" seccion=\\\"Medidas\\\"")
				.doesNotContain("\"zdr\"");
	}

	@Test
	void blankKeySendsNoAuthorizationHeader() {
		adapter("").generate("q", List.of(fragment())).block();

		assertThat(authorization.get()).isNull();
	}

	@Test
	void rateLimitBecomesAnUnavailableFailure() {
		status.set(429);

		assertThatThrownBy(() -> adapter("wire-test-key").generate("q", List.of(fragment())).block())
				.isInstanceOf(LLMUnavailableException.class)
				.hasMessageContaining("openrouter/" + MODEL + " returned HTTP 429");
	}

	private EvidenceFragment fragment() {
		return EvidenceFragment.create(UUID.randomUUID(), 0, "PROTO-001", "Medidas", "texto de evidencia",
				new float[] { 1f });
	}

	private OpenRouterHttpLLMAdapter adapter(String key) {
		OpenRouterProperties properties = new OpenRouterProperties(
				"http://127.0.0.1:" + server.getAddress().getPort(), MODEL, key, "deny");
		return new OpenRouterHttpLLMAdapter(WebClient.builder(), properties);
	}

	private static byte[] responseBody(int status) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			if (status != 200) {
				return mapper.writeValueAsBytes(mapper.createObjectNode().put("error", "rate limited"));
			}
			var root = mapper.createObjectNode();
			root.putArray("choices").addObject().putObject("message")
					.put("role", "assistant")
					.put("content", GROUNDED_ANSWER);
			return mapper.writeValueAsBytes(root);
		}
		catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

}
