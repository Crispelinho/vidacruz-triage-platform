package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
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
 * Spring AI ollama node with a fake {@link ChatModel}: no network. Subscriptions run on {@code parallel()} so the
 * BlockHound agent installed for the test JVM (TST-RXN) fails the test if the ChatClient path blocks.
 */
class SpringAiOllamaChatAdapterTest {

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
			throw new AssertionError("the ollama node must use stream(), not the blocking call()");
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
		SpringAiOllamaChatAdapter adapter = new SpringAiOllamaChatAdapter(chatModel, "llama3.1:8b");

		StepVerifier.create(adapter.generate("¿Cómo controlo un sangrado?", EVIDENCE)
				.subscribeOn(Schedulers.parallel()))
				.expectNext("{\"respuesta\":\"Presión directa [1]\",\"fuentes\":[]}")
				.verifyComplete();

		assertThat(adapter.key()).isEqualTo("ollama");
		assertThat(adapter.model()).isEqualTo("llama3.1:8b");
	}

	@Test
	void sendsTheSharedGroundedPromptVerbatim() {
		StreamingChatModel chatModel = new StreamingChatModel(Flux.just(chunk("ok")));
		SpringAiOllamaChatAdapter adapter = new SpringAiOllamaChatAdapter(chatModel, "llama3.1:8b");

		adapter.generate("¿Cómo controlo un sangrado?", EVIDENCE).block(Duration.ofSeconds(5));

		// JSON braces in the instruction must not be treated as template variables by ChatClient.
		assertThat(chatModel.lastPrompt.get().getContents())
				.isEqualTo(GroundedPrompt.build("¿Cómo controlo un sangrado?", EVIDENCE));
	}

	@Test
	void mapsServerErrorsToTheFailoverTrigger() {
		StreamingChatModel chatModel = new StreamingChatModel(Flux.error(WebClientResponseException
				.create(HttpStatus.SERVICE_UNAVAILABLE.value(), "Service Unavailable", null, null, null)));
		SpringAiOllamaChatAdapter adapter = new SpringAiOllamaChatAdapter(chatModel, "llama3.1:8b");

		StepVerifier.create(adapter.generate("q", EVIDENCE))
				.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(LLMUnavailableException.class)
						.hasMessage("ollama/llama3.1:8b returned HTTP 503"))
				.verify();
	}

	@Test
	void mapsTransportErrorsToTheFailoverTrigger() {
		StreamingChatModel chatModel = new StreamingChatModel(Flux.error(new IllegalStateException("connection refused")));
		SpringAiOllamaChatAdapter adapter = new SpringAiOllamaChatAdapter(chatModel, "llama3.1:8b");

		StepVerifier.create(adapter.generate("q", EVIDENCE))
				.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(LLMUnavailableException.class)
						.hasMessage("ollama/llama3.1:8b unavailable"))
				.verify();
	}

	@Test
	void routerFailsOverFromTheSpringAiNodeToTheNextNode() {
		SpringAiOllamaChatAdapter ollama = new SpringAiOllamaChatAdapter(
				new StreamingChatModel(Flux.error(new IllegalStateException("down"))), "llama3.1:8b");
		SpringAiOllamaChatAdapter backup = new SpringAiOllamaChatAdapter(
				new StreamingChatModel(Flux.just(chunk("respuesta"))), "backup-model");
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
		assertThatThrownBy(() -> new SpringAiOllamaChatAdapter(null, "m")).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new SpringAiOllamaChatAdapter(new StreamingChatModel(Flux.empty()), " "))
				.isInstanceOf(IllegalArgumentException.class);
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
