package com.vidacruz.clinical.infrastructure.adapters;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;

import org.springframework.ai.chat.client.ChatClient;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * A C17 chain node whose transport is a Spring AI {@link ChatClient}. One instance is created per provider, so a
 * heterogeneous chain is three qualified {@code ChatClient} beans behind the same {@link NamedLLMProvider} port.
 *
 * <p>The call uses {@code prompt().system(...).user(...).stream().content()}, never the blocking {@code call()} API.
 * The chunks are joined into one answer because {@link com.vidacruz.clinical.domain.services.RAGService} validates the
 * complete JSON before replying (RN-05). The shared {@link GroundedPrompt} and {@link ProviderFailures} are unchanged;
 * timeout, circuit breaker and failover stay in {@link ResilientLLMRouter}.
 *
 * <p>The Google GenAI SDK exposes its stream as a blocking {@code Iterable}; that node subscribes on
 * {@code boundedElastic} so the Netty event loop is never blocked (TST-RXN). Ollama and OpenAI-compatible models are
 * reactive WebClient streams and keep the default immediate subscription.
 */
public final class SpringAIChatAdapter implements NamedLLMProvider {

	private final String key;

	private final ChatClient chatClient;

	private final String model;

	/** Where the provider's stream subscription runs; {@code immediate} for reactive SDKs, boundedElastic for blocking ones. */
	private final Scheduler streamScheduler;

	public SpringAIChatAdapter(String key, ChatClient chatClient, String model) {
		this(key, chatClient, model, Schedulers.immediate());
	}

	public SpringAIChatAdapter(String key, ChatClient chatClient, String model, Scheduler streamScheduler) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("key must not be blank");
		}
		this.key = key;
		this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
		if (model == null || model.isBlank()) {
			throw new IllegalArgumentException("model must not be blank");
		}
		this.model = model;
		this.streamScheduler = Objects.requireNonNull(streamScheduler, "streamScheduler");
	}

	@Override
	public String key() {
		return key;
	}

	@Override
	public String model() {
		return model;
	}

	@Override
	public Mono<String> generate(String question, List<EvidenceFragment> evidence) {
		// M-1: trusted instruction in system role, delimited/escaped untrusted data in user message.
		Mono<String> answer = Mono.defer(() -> chatClient.prompt()
				.system(GroundedPrompt.systemInstruction())
				.user(GroundedPrompt.userContent(question, evidence))
				.stream()
				.content()
				.subscribeOn(streamScheduler)
				.collect(Collectors.joining()));
		return ProviderFailures.map(key(), model(), answer);
	}

}
