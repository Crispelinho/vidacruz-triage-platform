package com.vidacruz.clinical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.LLMCompletion;
import com.vidacruz.clinical.domain.ports.LLMRouter;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;
import com.vidacruz.clinical.infrastructure.adapters.SpringAIChatAdapter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/**
 * Opt-in live check of the Spring AI {@code ollama} node. It sets the same {@code spring.ai.ollama.*} keys used by
 * application.yaml, calls a real Ollama server and records counts, model ids and timings only. No question text,
 * answer text, patient data or credential value is persisted.
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_LIVE_VERIFICATION", matches = "1")
class OllamaLiveVerificationTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String BASE_URL = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://127.0.0.1:11434");

	private static final String MODEL = System.getenv().getOrDefault("OLLAMA_MODEL", "llama3.1:8b");

	private static final int RUNS = 5;

	private static final List<EvidenceFragment> EVIDENCE = List.of(EvidenceFragment.create(UUID.randomUUID(), 0,
			"PROTO-001", "Medidas iniciales", "Aplicar presión directa.", new float[] { 1f }));

	@Test
	void liveOllamaNodeIsReachableWithTheConfiguredStaticKeys() throws Exception {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withConfiguration(SpringAiOllamaTestAutoConfigurations.ALL)
				.withUserConfiguration(RAGConfiguration.class)
				.withBean(DataSource.class, () -> mock(DataSource.class))
				.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
				.withPropertyValues("spring.ai.ollama.base-url=" + BASE_URL,
						"spring.ai.ollama.chat.options.model=" + MODEL, "spring.ai.model.embedding=none",
						"rag.llm.chain=ollama", "rag.llm.timeout=300s");

		ObjectNode evidence = MAPPER.createObjectNode();
		evidence.put("generatedAt", Instant.now().toString());
		evidence.put("provider", "ollama");
		evidence.put("endpoint", BASE_URL + "/api/chat");
		evidence.put("configuredModel", MODEL);
		evidence.put("routerTimeout", "300s (overridden for live probe; production default 15s)");
		evidence.put("runs", RUNS);

		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(OllamaChatModel.class);
			NamedLLMProvider node = context.getBean("ollamaNode", NamedLLMProvider.class);
			assertThat(node).isInstanceOf(SpringAIChatAdapter.class);
			assertThat(node.model()).isEqualTo(MODEL);
			evidence.put("nodeClass", node.getClass().getSimpleName());
			evidence.put("nodeModel", node.model());

			LLMRouter router = context.getBean(LLMRouter.class);
			// Unmeasured warm-up: the first call can spend ~100 s loading tensors into the CPU model buffer.
			try {
				router.generate("¿Cómo controlo un sangrado?", EVIDENCE)
						.subscribeOn(Schedulers.parallel())
						.block(Duration.ofSeconds(600));
				evidence.put("warmup", "one unmeasured call before the five runs");
			} catch (Throwable failure) {
				evidence.put("warmup", "failed: " + failure.getClass().getSimpleName());
			}
			ArrayNode runsNode = evidence.putArray("runResults");
			java.util.concurrent.atomic.AtomicInteger passed = new java.util.concurrent.atomic.AtomicInteger();
			for (int run = 1; run <= RUNS; run++) {
				ObjectNode runNode = runsNode.addObject();
				runNode.put("run", run);
				long start = System.nanoTime();
				Mono<LLMCompletion> call = router.generate("¿Cómo controlo un sangrado?", EVIDENCE)
						.subscribeOn(Schedulers.parallel());
				try {
					LLMCompletion completion = call.block(Duration.ofSeconds(600));
					long elapsed = System.nanoTime() - start;
					runNode.put("status", "ok");
					runNode.put("provider", completion.provider());
					runNode.put("model", completion.model());
					runNode.put("answerChars", completion.answer().length());
					runNode.put("latencyMs", elapsed / 1_000_000);
					boolean ok = "ollama".equals(completion.provider()) && MODEL.equals(completion.model())
							&& !completion.answer().isBlank();
					runNode.put("checksPassed", ok);
					if (ok) {
						passed.incrementAndGet();
					}
				} catch (Throwable failure) {
					runNode.put("status", "failed");
					runNode.put("failureType", failure.getClass().getSimpleName());
					runNode.put("failureMessage", String.valueOf(failure.getMessage()).substring(0,
							Math.min(200, String.valueOf(failure.getMessage()).length())));
					runNode.put("latencyMs", (System.nanoTime() - start) / 1_000_000);
				}
			}
			evidence.put("passed", passed.get());
		});

		Path report = Path.of("build/reports/ollama-live-verification/results.json");
		Files.createDirectories(report.getParent());
		Files.writeString(report, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
		String override = System.getenv("OLLAMA_LIVE_RESULTS");
		if (override != null && !override.isBlank()) {
			Path extra = Path.of(override);
			Files.createDirectories(extra.getParent());
			Files.writeString(extra, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
		}
		assertThat(evidence.get("passed").asInt()).isEqualTo(RUNS);
	}

}
