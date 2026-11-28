package com.vidacruz.clinical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.LLMCompletion;
import com.vidacruz.clinical.domain.ports.LLMRouter;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;
import com.vidacruz.clinical.infrastructure.adapters.SpringAIChatAdapter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

/**
 * T-08 evidence: the {@code ollama} chain node runs on the Spring AI {@link OllamaChatModel} created by
 * {@code spring-ai-starter-model-ollama}, end to end through {@link LLMRouter}, against a local HTTP stub of
 * {@code POST /api/chat} (NDJSON streaming). Calls are subscribed on {@code parallel()} so BlockHound (TST-RXN)
 * would fail the test if the Spring AI streaming path blocked a non-blocking thread.
 */
class SpringAiOllamaWiringTest {

	private static final List<EvidenceFragment> EVIDENCE = List.of(EvidenceFragment.create(UUID.randomUUID(), 0,
			"PROTO-001", "Medidas iniciales", "Aplicar presión directa.", new float[] { 1f }));

	private static final String STREAMED_ANSWER = """
			{"model":"llama3.1:8b","created_at":"2026-11-27T00:00:00Z","message":{"role":"assistant","content":"{\\"respuesta\\":\\"Presión directa [1]\\","},"done":false}
			{"model":"llama3.1:8b","created_at":"2026-11-27T00:00:01Z","message":{"role":"assistant","content":"\\"fuentes\\":[]}"},"done":true,"done_reason":"stop","total_duration":1000,"load_duration":10,"prompt_eval_count":12,"prompt_eval_duration":100,"eval_count":8,"eval_duration":200}
			""";

	private final AtomicReference<String> requestBody = new AtomicReference<>();

	private DisposableServer ollamaStub;

	@AfterEach
	void stopStub() {
		if (ollamaStub != null) {
			ollamaStub.disposeNow();
		}
	}

	private void startStub(int status, String body) {
		ollamaStub = HttpServer.create()
				.host("localhost")
				.port(0)
				.route(routes -> routes.post("/api/chat", (request, response) -> request.receive()
						.aggregate()
						.asString()
						.flatMap(received -> {
							requestBody.set(received);
							return response.status(status)
									.header("Content-Type", "application/x-ndjson")
									.sendString(Mono.just(body))
									.then();
						})))
				.bindNow();
	}

	private ApplicationContextRunner runner() {
		return new ApplicationContextRunner().withConfiguration(SpringAiOllamaTestAutoConfigurations.ALL)
				.withUserConfiguration(RAGConfiguration.class)
				.withBean(DataSource.class, () -> mock(DataSource.class))
				.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
				.withPropertyValues("spring.ai.ollama.base-url=http://localhost:" + ollamaStub.port(),
						"spring.ai.ollama.chat.options.model=llama3.1:8b", "spring.ai.model.embedding=none",
						"rag.llm.chain=ollama");
	}

	@Test
	void ollamaNodeIsTheSpringAiAdapterOverTheStarterChatModel() {
		startStub(200, STREAMED_ANSWER);

		runner().run(context -> {
			assertThat(context).hasNotFailed();
			// Three heterogeneous ChatModels coexist; three qualified ChatClients are built from them.
			assertThat(context.getBeansOfType(ChatModel.class)).hasSize(3);
			assertThat(context).hasSingleBean(OllamaChatModel.class).hasSingleBean(GoogleGenAiChatModel.class)
					.hasSingleBean(OpenAiChatModel.class);
			assertThat(context.getBeansOfType(ChatClient.class)).hasSize(3);
			assertThat(context.getBeansOfType(EmbeddingModel.class)).isEmpty();

			NamedLLMProvider node = context.getBean("ollamaNode", NamedLLMProvider.class);
			assertThat(node).isInstanceOf(SpringAIChatAdapter.class);
			assertThat(node.model()).isEqualTo("llama3.1:8b");
		});
	}

	@Test
	void routerAnswersThroughSpringAiStreamingWithoutBlocking() {
		startStub(200, STREAMED_ANSWER);

		runner().run(context -> {
			LLMRouter router = context.getBean(LLMRouter.class);

			StepVerifier.create(router.generate("¿Cómo controlo un sangrado?", EVIDENCE)
					.subscribeOn(Schedulers.parallel()))
					.assertNext(completion -> assertThat(completion).isEqualTo(new LLMCompletion("ollama",
							"llama3.1:8b", "{\"respuesta\":\"Presión directa [1]\",\"fuentes\":[]}")))
					.verifyComplete();

			assertThat(requestBody.get()).contains("\"model\":\"llama3.1:8b\"")
					.contains("\"stream\":true")
					.contains("PROTO-001")
					.contains("Medidas iniciales")
					.contains("pregunta")
					.contains("evidencia");
		});
	}

	@Test
	void unavailableOllamaSurfacesAsTheDegradedModeTrigger() {
		startStub(503, "{\"error\":\"overloaded\"}");

		runner().run(context -> StepVerifier.create(context.getBean(LLMRouter.class)
				.generate("q", EVIDENCE)
				.subscribeOn(Schedulers.parallel()))
				.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(LLMUnavailableException.class)
						.hasMessage("ollama/llama3.1:8b returned HTTP 503"))
				.verify());
	}

}
