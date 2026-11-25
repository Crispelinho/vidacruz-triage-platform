# ADR-05: LLM provider and embedding model for the Clinical Assistant

- **Status:** Proposed. Not accepted. Option D is the proposed route; model, dimension, hardware, budget and ADR-11 evidence are still pending.
- **Date:** 2026-11-25
- **Deciders:** developer (pending)
- **Scope:** Choice of the chat model and the embedding model behind Spring AI for C-06. This ADR does not choose the Azure compute target (ADR-11), the vector store access model (ADR-04), the breaker values (ADR-09) or the relevance gate and citation validation (RN-05, R-10).
- **Related:** DEC-06, DEC-07, AMB-11, AMB-15, AMB-16, NFR-01, NFR-03, NFR-14, R-04, R-05, R-06, R-10, CA-G-01, CA-G-02, CA-G-05, CA-G-16, CA-G-22, TST-RXN, TST-RAG in [clinical-assistant-service.md](../services/clinical-assistant-service.md) and [requirements.md](../requirements.md)

## Context

DEC-06 requires the provider to be selected by configuration and mocked in tests. AMB-15 left the provider open. NFR-14 makes Azure mandatory, but the compute target belongs to ADR-11. The chat model and the embedding model must be reachable from that target.

Remote providers receive the question and the retrieved fragments on every query. As comparative context only, Azure OpenAI's default abuse monitoring is documented as retaining prompts and responses for about 30 days, with possible human review; opt-out depends on Microsoft approval. This is not the basis of the decision and must be checked against current Microsoft policy before it is cited elsewhere.

The embedding dimension fixes the vector column and the ANN index (CA-G-01, ADR-04). Changing the embedding model later requires re-ingesting the corpus and a migration. The relevance threshold is calibrated per embedding model (CA-G-02), so a threshold from one model is not valid for another.

Static inspection of Spring AI 1.1.1 artifacts confirms configuration names and defaults, not runtime behavior.

## Decision drivers

- Keep clinical text inside the agreed Azure perimeter where feasible.
- Keep the provider behind a configuration-driven interface with a mock in tests (DEC-06).
- Isolate the synchronous embedding call from the Netty event loop (NFR-01).
- Fix clinical evaluation criteria and pass rules before any run.
- Do not commit to a model, dimension, hardware or budget without evidence.

## Alternatives considered

| Option | Description | Pros | Cons | Status / why not chosen |
|---|---|---|---|---|
| **A. Azure OpenAI for chat and embeddings** | Use managed Azure OpenAI endpoints inside the Azure subscription | Fits NFR-14; one vendor for both models | Default retention and possible human review are documented; opt-out requires approval | Not selected now because Option D is proposed to keep clinical text inside the perimeter; retention uncertainty is comparative context only and requires a policy check before fallback |
| **B. OpenAI API (direct)** | Call the public OpenAI API from the Assistant | Simple setup | Clinical data leaves the Azure perimeter; terms for clinical data not reviewed | Rejected for this scope because it conflicts with the data-residency aim |
| **C. Anthropic API for chat with a separate embedding provider** | Combine two providers | Strong chat model | Two vendors, keys, terms and failure modes; embeddings still need a second provider | Rejected because it adds operational and privacy surface without evidence of benefit |
| **D. Local Ollama for chat and embeddings** | Serve both models inside the Azure environment | Clinical text does not leave the perimeter through the model call; no per-call vendor cost | Needs GPU capacity, model-quality evaluation and latency measurement | **Proposed**, not accepted. Viability depends on the ADR-11 evidence record and the evaluations in this ADR |

### Framework comparison required by DEC-06

This is a framework comparison, not a second provider list. Official documentation is cited directly; no feature equivalence is assumed.

| Framework | Evidence from official docs | Pros | Cons | Status |
|---|---|---|---|---|
| **Spring AI (pinned 1.1.1 under DEC-12)** | Spring reference documents Spring Boot auto-configuration for [Ollama chat](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html) and [embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html), vector-store auto-configuration including pgvector, and opt-in schema initialization in the [upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) | Fits the Boot 3.5 baseline; already pinned by DEC-12; shares the abstractions used by ADR-04 | Current reference artifact names may differ from 1.1.1 and require pinned-artifact verification; pgvector remains JDBC-based under DER-06 | **Proposed** as the framework by DEC-06; provider route is still pending |
| **LangChain4j** | Official docs list [20+ LLM providers, 30+ vector stores, ChatModel/EmbeddingModel, AI Services and RAG building blocks](https://docs.langchain4j.dev/intro/), the [ChatModel API](https://docs.langchain4j.dev/tutorials/chat-and-language-models/), an [Ollama integration](https://docs.langchain4j.dev/integrations/language-models/ollama/), and separate Spring Boot starters for Boot 3 and Boot 4 | Broad provider and store coverage; comparable model and RAG abstractions; has a Boot 3 starter | Not pinned by DEC-12; adding it would create a second framework surface; compatibility with the pinned Boot 3.5/Spring AI 1.1.1 stack is not verified | **Rejected for this scope**: changing the framework exceeds the provider decision in DEC-06 and would need a superseding ADR |

## Decision

**Proposed: Option D, Ollama served inside the agreed Azure perimeter.** The chat model and the embedding model are evaluated separately; they do not have to use the same hardware, latency budget or cost. ADR-11 records whether the chosen compute target can host the required workload in the target region and within quota and budget; ADR-05 uses that record and cannot be accepted without it. A negative record changes this route, not ADR-11 automatically.

Spring AI 1.1.1 is pinned by DEC-12. Static inspection identifies `spring.ai.ollama.base-url`, `spring.ai.ollama.chat.options.model` and `spring.ai.ollama.embedding.options.model` as the primary keys. `spring.ai.ollama.embedding.model` is an alias. `spring.ai.model.chat=ollama` and `spring.ai.model.embedding=ollama` are optional in 1.1.1 because the autoconfigurations use `matchIfMissing = true`; set them explicitly if more than one provider is on the classpath. The removed 1.0 keys `spring.ai.ollama.chat.enabled` and `spring.ai.ollama.embedding.enabled` must not be used. Runtime behavior remains pending a spike.

`EmbeddingModel.call()` is synchronous in the inspected 1.1.1 code and uses `RestClient` in blocking mode; no reactive isolation was found in the inspected embedding path. The adapter must isolate the embedding call on a bounded scheduler as in ADR-04. Whether it shares the pgvector scheduler is not decided here.

A startup check of the endpoint and model is proposed as a separate check under CA-G-22. It does not replace the runtime readiness rule in ADR-09, which excludes LLM state from readiness.

## Consequences

- The vector dimension becomes a fixed constant in the migration; changing the embedding model requires full re-ingestion.
- GPU capacity becomes a deployment dependency. If it is unavailable, the Assistant returns `503 LLM_UNAVAILABLE` per NFR-03 and ADR-09; Triage is unaffected. Provider availability is recorded as a metric, never as a readiness gate, and LLM-dependent endpoints recover automatically when the provider returns (developer decision, 2026-11-25).
- Under Option D, model calls stay inside the Azure perimeter. Logs, telemetry and traces are still in scope for CA-G-16.
- Tests keep a mocked model (DEC-06). Threshold calibration needs the real model in the test environment, not in unit tests.
- If ADR-11 cannot host the workload, this ADR must be updated before any provider route is accepted.

## Risks and mitigations

- **R-04 (LLM latency and cost during evaluation):** use timeouts, the ADR-09 circuit breaker and a mocked model in tests; measure latency and cost before accepting Option D. Clinical text in logs and metric labels is tracked under CA-G-16 and NFR-19.
- **R-05 (version drift):** pin Spring AI 1.1.1 under DEC-12 and run the runtime spike before relying on static configuration findings.
- **R-06 (Azure access or quota):** use the ADR-11 evidence record; do not assume GPU, region or quota.
- **R-10 (relevance and citations):** calibrate threshold and top-k per embedding model under CA-G-02 and AMB-16.
- **CA-G-05 (failure classification):** propose `503 LLM_UNAVAILABLE` for open-circuit and timeout cases; leave 429 and 5xx classification to ADR-09.
- **CA-G-22 (readiness):** keep the proposed startup check separate from runtime readiness.
- **G-19 (test infrastructure):** add Testcontainers and BlockHound before the runtime verification can run.
- **PROTO-003 ambiguity:** the literal text assigns 5 % to both mild 3–5 % and moderate 5–8 %. The protocol owner must resolve the boundary; no probe at that boundary is used.

## Verification

- **Runtime configuration spike:** start the pinned Spring AI 1.1.1 Ollama autoconfiguration and confirm the static keys above are effective.
- **TST-RXN embedding control:** BlockHound detects a direct embedding call from a reactor thread and accepts the call through the bounded scheduler, with positive and negative controls.
- **TST-RAG:** PROTO-001 and PROTO-002 return the expected content with the mandatory citation. PROTO-003 uses only the unambiguous probes: 4 % mild, 6.5 % moderate, 9 % severe, and 4 % with altered mental status as severe. The proposed pass rule is five out of five runs per probe, fixed before the run.
- **Negative query:** a question without a matching protocol returns the fixed notice with no clinical content (RN-05.3).
- **ADR-11 evidence:** a recorded answer on whether the chosen Azure target can host the workload, including GPU where required, in the target region and within budget.
- **Calibration:** threshold and top-k are recorded for the chosen embedding model before acceptance.
- No ADR in this line is accepted until these verifications pass and the developer approves the result.
