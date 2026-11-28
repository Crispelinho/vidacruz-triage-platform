package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.LLMCompletion;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/**
 * Spring AI chat nodes with a fake {@link ChatModel}: no network. Subscriptions run on {@code parallel()} so the
 * BlockHound agent installed for the test JVM (TST-RXN) fails the test if the ChatClient path blocks.
 */
class SpringAIChatAdapterTest {

	private static final List<EvidenceFragment> EVIDENCE = List.of(EvidenceFragment.create(UUID.randomUUID(), 0,
			"PROTO-001", "Medidas iniciales", "Aplicar presión directa.", new float[] { 1f }));

	/** Streaming-only fake: {@code call()} fails so a regression to the blocking API is caught. */
	private static final class StreamingChatModel implements ChatModel {

		private final Flux<ChatResponse> responses;

		private final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();

		StreamingChatModel(Flux<ChatResponse> responses) {
			this.responses = responses;
		}

		@Override
		public ChatResponse call(Prompt prompt) {
			throw new AssertionError("chat nodes must use stream(), not the blocking call()");
		}

		@Override
		public Flux<ChatResponse> stream(Prompt prompt) {
			lastPrompt.set(prompt);
			return responses;
		}

	}

	private static ChatResponse chunk(String text) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
	}

	@Test
	void joinsStreamedChunksIntoOneAnswerWithoutBlocking() {
		StreamingChatModel chatModel = new StreamingChatModel(
				Flux.just(chunk("{\"respuesta\":\"Presión "), chunk("directa [1]\","), chunk("\"fuentes\":[]}")));
		SpringAIChatAdapter adapter = adapter("ollama", chatModel, "llama3.1:8b");

		StepVerifier.create(adapter.generate("¿Cómo controlo un sangrado?", EVIDENCE)
				.subscribeOn(Schedulers.parallel()))
				.expectNext("{\"respuesta\":\"Presión directa [1]\",\"fuentes\":[]}")
				.verifyComplete();

		assertThat(adapter.key()).isEqualTo("ollama");
		assertThat(adapter.model()).isEqualTo("llama3.1:8b");
	}

	@Test
	void oneAdapterClassServesAllThreeChainKeys() {
		for (String key : List.of("ollama", "gemini", "opencode")) {
			SpringAIChatAdapter node = adapter(key, new StreamingChatModel(Flux.just(chunk("ok"))), key + "-model");
			assertThat(node.key()).isEqualTo(key);
			assertThat(node.model()).isEqualTo(key + "-model");
		}
	}

	@Test
	void sendsTheSharedGroundedPromptVerbatim() {
		StreamingChatModel chatModel = new StreamingChatModel(Flux.just(chunk("ok")));
		SpringAIChatAdapter adapter = adapter("ollama", chatModel, "llama3.1:8b");

		adapter.generate("¿Cómo controlo un sangrado?", EVIDENCE).block(Duration.ofSeconds(5));

		// M-1: trusted instruction goes in system role, delimited data in user content.
		assertThat(chatModel.lastPrompt.get().getSystemMessage().getText())
				.isEqualTo(GroundedPrompt.systemInstruction());
		assertThat(chatModel.lastPrompt.get().getUserMessage().getText())
				.isEqualTo(GroundedPrompt.userContent("¿Cómo controlo un sangrado?", EVIDENCE));
	}

	@Test
	void mapsServerErrorsToTheFailoverTrigger() {
		StreamingChatModel chatModel = new StreamingChatModel(Flux.error(WebClientResponseException
				.create(HttpStatus.SERVICE_UNAVAILABLE.value(), "Service Unavailable", null, null, null)));
		SpringAIChatAdapter adapter = adapter("ollama", chatModel, "llama3.1:8b");

		StepVerifier.create(adapter.generate("q", EVIDENCE))
				.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(LLMUnavailableException.class)
						.hasMessage("ollama/llama3.1:8b returned HTTP 503"))
				.verify();
	}

	@Test
	void mapsTransportErrorsToTheFailoverTrigger() {
		StreamingChatModel chatModel = new StreamingChatModel(Flux.error(new IllegalStateException("connection refused")));
		SpringAIChatAdapter adapter = adapter("ollama", chatModel, "llama3.1:8b");

		StepVerifier.create(adapter.generate("q", EVIDENCE))
				.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(LLMUnavailableException.class)
						.hasMessage("ollama/llama3.1:8b unavailable"))
				.verify();
	}

	@Test
	void routerFailsOverFromTheSpringAiNodeToTheNextNode() {
		SpringAIChatAdapter ollama = adapter("ollama",
				new StreamingChatModel(Flux.error(new IllegalStateException("down"))), "llama3.1:8b");
		SpringAIChatAdapter backup = adapter("gemini", new StreamingChatModel(Flux.just(chunk("respuesta"))),
				"backup-model");
		ResilientLLMRouter router = new ResilientLLMRouter(List.of(ollama, renamed(backup, "gemini")),
				CircuitBreakerRegistry.ofDefaults(), Duration.ofSeconds(2),
				new LlmCallMetrics(new SimpleMeterRegistry()));

		StepVerifier.create(router.generate("q", EVIDENCE).subscribeOn(Schedulers.parallel()))
				.assertNext(completion -> assertThat(completion).isEqualTo(
						new LLMCompletion("gemini", "backup-model", "respuesta")))
				.verifyComplete();
	}

	@Test
	void rejectsMissingCollaborators() {
		assertThatThrownBy(() -> new SpringAIChatAdapter("ollama", null, "m"))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new SpringAIChatAdapter(" ", ChatClient.create(new StreamingChatModel(Flux.empty())), "m"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SpringAIChatAdapter("ollama",
				ChatClient.create(new StreamingChatModel(Flux.empty())), "m", null))
				.isInstanceOf(NullPointerException.class);
	}

	/** One adapter class, one chain key per provider; all three C17 nodes use this exact path. */
	private static SpringAIChatAdapter adapter(String key, ChatModel model, String name) {
		return new SpringAIChatAdapter(key, ChatClient.create(model), name);
	}

	/** Same Spring AI adapter class under another chain key, to exercise failover between two nodes. */
	private static NamedLLMProvider renamed(NamedLLMProvider delegate, String key) {
		return new NamedLLMProvider() {
			@Override
			public String key() {
				return key;
			}

			@Override
			public String model() {
				return delegate.model();
			}

			@Override
			public Mono<String> generate(String question, List<EvidenceFragment> evidence) {
				return delegate.generate(question, evidence);
			}
		};
	}

}
