# ADR-05 (superseded): LLM Provider and Framework — Spring AI 1.1.x GA

**Status:** Superseded on 2026-11-27 by [ADR-05-assistant-llm-provider.md](ADR-05-assistant-llm-provider.md). Retained for history; do not use it as the current ADR-05 or as implementation/acceptance criteria.
**Date:** 2026-11-25
**Superseded:** 2026-11-27
**Original decision:** Use Spring AI 1.1.x GA as the LLM abstraction layer; provider chosen at deployment time via configuration.

> The canonical ADR preserves the framework comparison from this draft, replaces the stale `spring.ai.provider`/`EmbeddingClient` wording, records the C17 chat chain and the dedicated `WebClient` adapters, and keeps ADR-05 `Proposed` until verification passes.

---

## Context

The brief requires RAG (Retrieval-Augmented Generation) for the Clinical Assistant (RF-04-A) and resilience to LLM failure (NFR-03, DEC-07). Two frameworks are commonly used:
1. **Spring AI 1.1.x GA:** Spring-native abstraction for LLM providers, embeddings, and vector stores.
2. **LangChain4j:** Java wrapper around LangChain, agnostic to Spring.

**Constraints:**
- Spring Boot 3.5.x (ADR-10): Spring AI 2.x requires Boot 4.x; Spring AI 1.1.x is the Boot 3.5 line.
- pgvector as vector store (DEC-07): Spring AI 1.1.x has a pgvector starter (`spring-ai-pgvector-store-spring-boot-starter`).
- R2DBC for async database access (NFR-01): Spring AI's pgvector starter uses JDBC (blocking). Requires isolation on a bounded elastic scheduler (DER-06, risk R-03).
- Multiple LLM providers: The choice should be configurable (OpenAI, Anthropic, Ollama, etc.).

---

## Problem

1. **Framework choice:** Spring AI and LangChain4j are both viable but differ in:
   - Ecosystem integration (Spring AI is tighter to Spring Boot).
   - Complexity (Spring AI is leaner; LangChain4j is more feature-rich).
   - Maintenance burden (Spring AI is maintained by VMware/Pivotal; LangChain4j by LangChain).

2. **Blocking I/O risk:** Spring AI's pgvector starter uses JDBC (blocking), which can block the event loop if not isolated.
   - **Mitigation:** Use `Schedulers.boundedElastic()` or an async adapter (DER-06, R-03).

3. **Provider flexibility:** We want to swap LLM providers (OpenAI, Anthropic, Ollama) without rewriting code.
   - **Solution:** Use Spring AI's abstraction; the provider is a configuration property (`spring.ai.provider`).

---

## Alternatives Considered

| Alternative | Pros | Cons |
|---|---|---|
| **Spring AI 1.1.x GA** (recommended) | Native Spring Boot 3.5 support; pgvector starter; provider-agnostic abstraction; active maintenance | pgvector starter uses JDBC (blocking); requires bounded elastic isolation (DER-06); smaller ecosystem than LangChain |
| **LangChain4j** | More features; language-agnostic design; active community | Not Spring-native; requires manual Spring integration; pgvector support is less mature; larger learning curve |
| **Raw SDK calls (OpenAI, Anthropic)** | Full control; no abstraction overhead | Ties code to one provider; no easy migration; more boilerplate; anti-pattern for microservices |
| **Ollama (local LLM)** | No external API calls; runs locally; privacy-respecting | Limited to CPU/GPU available; slower inference; not suitable for production use; can be used as a fallback for testing |

---

## Decision

**Use Spring AI 1.1.x GA with the following constraints:**

1. **LLM abstraction:**
   - Code depends only on Spring AI's `ChatClient` and `EmbeddingClient` interfaces.
   - No direct provider SDK calls in the Clinical Assistant service.

2. **Provider configuration:**
   - `spring.ai.provider`: Enum or string property (default: `OPENAI`).
   - Supported providers: `OPENAI`, `ANTHROPIC`, `OLLAMA`.
   - Each provider's API key is injected via environment variables (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `OLLAMA_BASE_URL`).

3. **Vector store:**
   - Use Spring AI's pgvector starter (`spring-ai-pgvector-store-spring-boot-starter`).
   - **Blocking I/O isolation:** Pgvector operations (write embeddings, retrieve by similarity) run on a bounded elastic scheduler, not the WebFlux event loop.
   - Measure latency; document the threshold where blocking becomes unacceptable (DER-06).

4. **Fallback for testing:**
   - Provide a mock `ChatClient` for unit tests.
   - Use Testcontainers' PostgreSQL image for pgvector integration tests.
   - Ollama can run in docker-compose for local testing without external API calls.

---

## Configuration Example

```yaml
spring:
  ai:
    provider: OPENAI
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini  # or gpt-3.5-turbo for cost
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      model: claude-3-haiku
    ollama:
      base-url: http://ollama:11434
      model: llama2

triage:
  vector-store: pgvector
```

---

## Consequences

### Positive
- **Provider flexibility:** Swap LLM providers by changing a configuration property; no code changes.
- **Cost optimization:** Use cheaper models (e.g., gpt-3.5-turbo) during evaluation; upgrade to gpt-4o-mini in production.
- **Local testing:** Ollama allows testing RAG without external API calls or quota limits.
- **Spring Boot native:** Spring AI integrates seamlessly with Spring Boot 3.5 and R2DBC.
- **No vendor lock-in:** Switch from OpenAI to Anthropic or Ollama by updating environment variables.

### Negative
- **pgvector blocking I/O:** Spring AI's pgvector starter uses JDBC (blocking). Requests to retrieve embeddings block a thread.
  - **Mitigation:** Isolate on `Schedulers.boundedElastic()` (bounded thread pool for blocking operations). Measure latency; if excessive (> 100ms per query), consider:
    - Switching to a non-blocking vector store (not available in Spring AI 1.1.x; future work).
    - Caching embeddings in Redis (trade-off: stale data).
    - Sampling retrieval (trade-off: less accurate RAG results).

- **Spring AI 1.1.x is not the latest:** 2.x is in development; 1.1.x is LTS until Spring Boot 3.5 goes EOL (June 2026, already passed). After that, Spring AI 1.1.x receives no updates.
  - **Mitigation:** Plan Boot 4.x migration post-delivery; Spring AI 2.x will be available by then with improvements to pgvector and other vector stores.

- **Provider-specific features not exposed:** Some advanced features (e.g., function calling, structured output) may not be fully supported by Spring AI's abstraction.
  - **Mitigation:** For this evaluation, use basic chat and embedding endpoints. Document as a future enhancement.

---

## Risks

- **R-04 (LLM latency and cost):** External LLM calls introduce latency (500ms–2s) and cost (USD 0.001–0.1 per query).
  - **Probability:** High (inherent to the architecture).
  - **Impact:** High (slow responses; API quota exceeded).
  - **Mitigation:** Set a timeout (5 seconds), circuit breaker (ADR-09), and cost limits. Use Ollama locally for development; pay-as-you-go providers in production.

- **R-03 (pgvector JDBC blocking):** Each embedding retrieval on the main thread can block the event loop.
  - **Probability:** Medium (depends on query complexity and database latency).
  - **Impact:** Medium (request latency increases; throughput drops).
  - **Mitigation:** Measure in change 8 (clinical-assistant-rag). If p99 latency > 500ms, offload to bounded elastic.

- **R-10 (anti-hallucination failure):** LLM may cite sources not in the corpus or hallucinate content.
  - **Probability:** Medium (common LLM failure mode).
  - **Impact:** High (incorrect clinical information).
  - **Mitigation:** Implement citation validation (DEC-07, ADR-09) and relevance gate before LLM call. Test with the three acceptance questions (§12 of requirements.md).

---

## References

- Brief: RF-04-A (RAG query), NFR-03 (circuit breaker on LLM failure), TC-06 (Spring AI or LangChain4j choice)
- Decision register: DEC-06 (Spring AI abstraction), DEC-07 (relevance gate and circuit breaker)
- Risk register: R-03 (pgvector blocking), R-04 (LLM latency), R-10 (anti-hallucination)
- Related ADRs: ADR-10 (Boot 3.5), ADR-09 (circuit breaker), ADR-04 (pgvector store)
- OpenSpec change: 8 (clinical-assistant-rag)
- LLM provider testing strategy: Mock for unit tests; Ollama for integration tests; real API in staging.

---

## Post-Decision Actions

1. Verify Spring AI 1.1.x latest GA on spring.io before building.
2. Confirm pgvector starter artifact name and version compatibility.
3. Set up Ollama in docker-compose for local development.
4. Create a mock `ChatClient` bean for test profiles.
5. Measure pgvector latency in change 8; document the baseline.
