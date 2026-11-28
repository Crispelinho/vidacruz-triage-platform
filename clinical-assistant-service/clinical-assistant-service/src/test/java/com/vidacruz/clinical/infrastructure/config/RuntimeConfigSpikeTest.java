package com.vidacruz.clinical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.LLMRouter;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;
import com.vidacruz.clinical.infrastructure.adapters.GeminiEmbeddingAdapter;
import com.vidacruz.clinical.infrastructure.adapters.LexicalEmbeddingAdapter;
import com.vidacruz.clinical.infrastructure.adapters.OpenRouterHttpLLMAdapter;
import com.vidacruz.clinical.infrastructure.adapters.PiiRedactingLLMProvider;
import com.vidacruz.clinical.infrastructure.adapters.ResilientLLMRouter;
import com.vidacruz.clinical.infrastructure.adapters.SpringAIChatAdapter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * ADR-05 runtime configuration spike (harness): proves custom config keys bind and select the intended beans.
 *
 * <p>Covered: rag.llm.chain, ollama.*, gemini.*, opencode.*, openrouter.*, and clinical.vector-store.embedding-provider.
 * Not covered: live provider calls or network reachability. The Spring AI {@code spring.ai.ollama.*} keys behind
 * {@code ollamaNode} are exercised against a local stub in {@link SpringAiOllamaWiringTest}.
 */
class RuntimeConfigSpikeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(SpringAiOllamaTestAutoConfigurations.ALL)
            .withUserConfiguration(RAGConfiguration.class)
            .withBean(DataSource.class, () -> org.mockito.Mockito.mock(DataSource.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void defaultChainBindsCustomKeysAndLexicalEmbedding() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();

            LLMRouterProperties chain = context.getBean(LLMRouterProperties.class);
            assertThat(chain.providerKeys()).containsExactly("ollama", "gemini", "opencode");
            assertThat(chain.timeout()).isEqualTo(Duration.ofSeconds(15));

            GroundingProperties grounding = context.getBean(GroundingProperties.class);
            assertThat(grounding.markerlessMinOverlap()).isEqualTo(0.2d);
            assertThat(grounding.minLexicalOverlap()).isEqualTo(0.2d);
            assertThat(grounding.requireMarkers()).isFalse();
            assertThat(grounding.numbersMustAppearInEvidence()).isFalse();

            OllamaProperties ollama = context.getBean(OllamaProperties.class);
            assertThat(ollama.url()).isEqualTo("http://localhost:11434");
            assertThat(ollama.model()).isEqualTo("llama3.1:8b");
            assertThat(ollama.timeout()).isEqualTo(Duration.ofSeconds(15));

            GeminiProperties gemini = context.getBean(GeminiProperties.class);
            assertThat(gemini.baseUrl()).isEqualTo("https://generativelanguage.googleapis.com/v1beta/models");
            assertThat(gemini.model()).isEqualTo("gemini-3.5-flash-lite");
            assertThat(gemini.apiKey()).isEmpty();

            OpenCodeProperties opencode = context.getBean(OpenCodeProperties.class);
            assertThat(opencode.url()).isEqualTo("https://opencode.ai/inference/openai/v1/chat/completions");
            assertThat(opencode.model()).isEqualTo("glm-5.3");
            assertThat(opencode.apiKey()).isEmpty();

            OpenRouterProperties openRouter = context.getBean(OpenRouterProperties.class);
            assertThat(openRouter.baseUrl()).isEqualTo("https://openrouter.ai/api/v1");
            assertThat(openRouter.model()).isEqualTo("nvidia/nemotron-3.5-lightning:free");
            assertThat(openRouter.apiKey()).isEmpty();
            assertThat(openRouter.dataCollection()).isEqualTo("deny");

            assertThat(context.getBean("ollamaNode", NamedLLMProvider.class)).isInstanceOf(SpringAIChatAdapter.class);
            assertThat(context.getBean("geminiNode", NamedLLMProvider.class)).isInstanceOf(PiiRedactingLLMProvider.class);
            assertThat(context.getBean("opencodeNode", NamedLLMProvider.class)).isInstanceOf(PiiRedactingLLMProvider.class);
            assertThat(context.getBean("openRouterNode", NamedLLMProvider.class)).isInstanceOf(OpenRouterHttpLLMAdapter.class);
            assertThat(context.getBean(LLMRouter.class)).isInstanceOf(ResilientLLMRouter.class);

            VectorStoreProperties vector = context.getBean(VectorStoreProperties.class);
            assertThat(vector.dimensions()).isEqualTo(768);
            assertThat(vector.relevanceThreshold()).isEqualTo(VectorStoreProperties.CALIBRATED_RELEVANCE_THRESHOLD);
            assertThat(context.getBean(EmbeddingProvider.class)).isInstanceOf(LexicalEmbeddingAdapter.class);
        });
    }

    @Test
    void customLlmAndEmbeddingKeysAreBound() {
        runner.withPropertyValues(
                "rag.llm.chain=gemini,ollama",
                "rag.llm.timeout=7s",
                "ollama.url=http://ollama-host:11434",
                "ollama.model=llama3.2:3b",
                "ollama.timeout=9s",
                "gemini.api-key=test-gemini-key",
                "gemini.model=gemini-3.5-flash-lite",
                "opencode.api-key=test-opencode-key",
                "opencode.model=glm-custom",
                "openrouter.api-key=test-openrouter-key",
                "openrouter.model=openrouter/free",
                "openrouter.base-url=http://router-host:9000/v1",
                "clinical.vector-store.embedding-provider=gemini",
                "gemini.embedding.model=gemini-embedding-2",
                "gemini.embedding.dimensions=768")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(LLMRouterProperties.class).providerKeys()).containsExactly("gemini", "ollama");
                assertThat(context.getBean(LLMRouterProperties.class).timeout()).isEqualTo(Duration.ofSeconds(7));

                assertThat(context.getBean(OllamaProperties.class).url()).isEqualTo("http://ollama-host:11434");
                assertThat(context.getBean(OllamaProperties.class).model()).isEqualTo("llama3.2:3b");
                assertThat(context.getBean(OllamaProperties.class).timeout()).isEqualTo(Duration.ofSeconds(9));
                assertThat(context.getBean(GeminiProperties.class).apiKey()).isEqualTo("test-gemini-key");
                assertThat(context.getBean(OpenCodeProperties.class).apiKey()).isEqualTo("test-opencode-key");
                assertThat(context.getBean(OpenCodeProperties.class).model()).isEqualTo("glm-custom");
                OpenRouterProperties openRouter = context.getBean(OpenRouterProperties.class);
                assertThat(openRouter.baseUrl()).isEqualTo("http://router-host:9000/v1");
                assertThat(openRouter.model()).isEqualTo("openrouter/free");
                assertThat(openRouter.apiKey()).isEqualTo("test-openrouter-key");

                assertThat(context.getBean(EmbeddingProvider.class)).isInstanceOf(GeminiEmbeddingAdapter.class);
                GeminiEmbeddingProperties embedding = context.getBean(GeminiEmbeddingProperties.class);
                assertThat(embedding.model()).isEqualTo("gemini-embedding-2");
                assertThat(embedding.dimensions()).isEqualTo(768);
            });
    }

    @Test
    void unknownChainNodeFailsFast() {
        runner.withPropertyValues("rag.llm.chain=gemini,unknown")
            .run(context -> assertThat(context).hasFailed());
    }
}
