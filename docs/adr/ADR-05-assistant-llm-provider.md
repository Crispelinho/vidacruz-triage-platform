# ADR-05: LLM provider, framework and embedding model for the Clinical Assistant

- **Status:** Proposed. Not accepted. This is the canonical ADR-05 since 2026-11-27; `ADR-05-llm-provider-spring-ai.md` is superseded by this document. The three-node chat chain was recorded by the Developer/User on 2026-11-26 (C17), but the embedding model, dimension, cost evidence and verification results are still pending.
- **Date:** 2026-11-25 (consolidated 2026-11-27)
- **Deciders:** developer; provider chain recorded 2026-11-26, ADR acceptance still pending verification
- **Scope:** Choice of the chat route, the framework abstraction and the embedding model behind the Clinical Assistant for C-06. This ADR does not choose the Azure compute target (ADR-11), the vector store access model (ADR-04), the breaker values (ADR-09) or the relevance gate and citation validation (RN-05, R-10).
- **Related:** DEC-06, DEC-07, DEC-12, AMB-11, AMB-15, AMB-16, NFR-01, NFR-03, NFR-14, R-03, R-04, R-05, R-06, R-10, CA-G-01, CA-G-02, CA-G-05, CA-G-16, CA-G-22, TST-RXN, TST-RAG, OD-5 in [clinical-assistant-service.md](../services/clinical-assistant-service.md) and [requirements.md](../requirements.md)

## Context

DEC-06 requires the provider to be selected by configuration and mocked in tests. AMB-15 left the provider open. NFR-14 makes Azure mandatory, but the compute target belongs to ADR-11. The chat nodes and the embedding model must be reachable from that target.

Remote providers receive the question and the retrieved fragments on every query. As comparative context only, Azure OpenAI's default abuse monitoring is documented as retaining prompts and responses for about 30 days, with possible human review; opt-out depends on Microsoft approval. This is not the basis of the decision and must be checked against current Microsoft policy before it is cited elsewhere.

The embedding dimension fixes the vector column and the ANN index (CA-G-01, ADR-04). Changing the embedding model later requires re-ingesting the corpus and a migration. The relevance threshold is calibrated per embedding model (CA-G-02), so a threshold from one model is not valid for another.

Static inspection of Spring AI 1.1.1 artifacts confirms configuration names and defaults, not runtime behavior. No runtime spike has recorded those keys as effective.

## Decision drivers

- Keep clinical text inside the agreed perimeter where feasible, and record explicitly when a route sends it outside.
- Keep the provider behind a configuration-driven interface with a mock in tests (DEC-06).
- Isolate the synchronous embedding call from the Netty event loop (NFR-01, DER-06, R-03).
- Fix clinical evaluation criteria and pass rules before any run.
- Do not commit to a model, dimension, hardware or budget without evidence.

## Alternatives considered

| Option | Description | Pros | Cons | Status / why not chosen |
|---|---|---|---|---|
| **A. Azure OpenAI for chat and embeddings** | Use managed Azure OpenAI endpoints inside the Azure subscription | Fits NFR-14; one vendor for both models | Default retention and possible human review are documented; opt-out requires approval | Not selected now because Option D was proposed to keep clinical text inside the perimeter; retention uncertainty is comparative context only and requires a policy check before fallback |
| **B. OpenAI API (direct)** | Call the public OpenAI API from the Assistant | Simple setup | Clinical data leaves the Azure perimeter; terms for clinical data not reviewed | Rejected for this scope because it conflicts with the data-residency aim |
| **C. Anthropic API for chat with a separate embedding provider** | Combine two providers | Strong chat model | Two vendors, keys, terms and failure modes; embeddings still need a second provider | Rejected because it adds operational and privacy surface without evidence of benefit |
| **D. Local Ollama for chat and embeddings** | Serve both models inside the Azure environment | Clinical text does not leave the perimeter through the model call; no per-call vendor cost | Needs GPU capacity, model-quality evaluation and latency measurement | **Proposed for the embedding route; its chat part was amended by the C17 chain recorded below.** Viability still depends on the ADR-11 evidence record and the evaluations in this ADR |

### Framework comparison required by DEC-06

This is a framework comparison, not a second provider list. Official documentation is cited directly; no feature equivalence is assumed.

| Framework | Evidence from official docs | Pros | Cons | Status |
|---|---|---|---|---|
| **Spring AI (pinned 1.1.1 under DEC-12)** | Spring reference documents Spring Boot auto-configuration for [Ollama chat](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html) and [embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html), vector-store auto-configuration including pgvector, and opt-in schema initialization in the [upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) | Fits the Boot 3.5 baseline; already pinned by DEC-12; shares the abstractions used by ADR-04 | Current reference artifact names may differ from 1.1.1 and require pinned-artifact verification; pgvector remains JDBC-based under DER-06 | **Proposed** as the framework by DEC-06; the current LLM chain does not use its chat abstraction (see Implementation status) |
| **LangChain4j** | Official docs list [20+ LLM providers, 30+ vector stores, ChatModel/EmbeddingModel, AI Services and RAG building blocks](https://docs.langchain4j.dev/intro/), the [ChatModel API](https://docs.langchain4j.dev/tutorials/chat-and-language-models/), an [Ollama integration](https://docs.langchain4j.dev/integrations/language-models/ollama/), and separate Spring Boot starters for Boot 3 and Boot 4 | Broad provider and store coverage; comparable model and RAG abstractions; has a Boot 3 starter | Not pinned by DEC-12; adding it would create a second framework surface; compatibility with the pinned Boot 3.5/Spring AI 1.1.1 stack is not verified | **Rejected for this scope**: changing the framework exceeds the provider decision in DEC-06 and would need a superseding ADR |

## Decision

**Proposed: Option D for the embedding route**, with the chat route governed by the C17 record below. The chat model and the embedding model are evaluated separately; they do not have to use the same hardware, latency budget or cost. ADR-11 records whether the chosen compute target can host the required workload in the target region and within quota and budget; ADR-05 uses that record and cannot be accepted without it. A negative record changes this route, not ADR-11 automatically.

Spring AI 1.1.1 is pinned by DEC-12. Static inspection identifies `spring.ai.ollama.base-url`, `spring.ai.ollama.chat.options.model` and `spring.ai.ollama.embedding.options.model` as the primary keys. `spring.ai.ollama.embedding.model` is an alias. `spring.ai.model.chat=ollama` and `spring.ai.model.embedding=ollama` are optional in 1.1.1 because the autoconfigurations use `matchIfMissing = true`; set them explicitly if more than one provider is on the classpath. The removed 1.0 keys `spring.ai.ollama.chat.enabled` and `spring.ai.ollama.embedding.enabled` must not be used. The `EmbeddingModel` interface inspected in 1.1.1 is the relevant abstraction; the earlier draft's `EmbeddingClient` name is not part of this decision. The earlier draft's generic `spring.ai.provider` property was not verified against 1.1.1 and is not part of this decision. Runtime behavior remains pending a spike.

`EmbeddingModel.call()` is synchronous in the inspected 1.1.1 code and uses `RestClient` in blocking mode; no reactive isolation was found in the inspected embedding path. The adapter must isolate the embedding call on a bounded scheduler as in ADR-04. Whether it shares the pgvector scheduler is not decided here.

A startup check of the endpoint and model is proposed as a separate check under CA-G-22. It does not replace the runtime readiness rule in ADR-09, which excludes LLM state from readiness.

### Developer decision recorded 2026-11-26 (C17 — LLM failover chain)

The Developer/User recorded a three-node chat chain, configured by `RAG_LLM_CHAIN` (`rag.llm.chain`):

| Order | Node | Endpoint / source | Model | Cost characterization |
|---|---|---|---|---|
| 1 | `ollama` | Local Ollama exposed through Cloudflare Tunnel via `OLLAMA_BASE_URL` | `llama3.1:8b` (alternative `mistral:7b`) | Already-owned hardware; no new Azure GPU spend |
| 2 | `gemini` | Google AI Studio free tier, `generateContent` endpoint | `gemini-2.5-flash` (declared by the Developer/User) | Declared free by the Developer/User; exact model id still to be verified with Google |
| 3 | `opencode` | `https://opencode.ai/inference/openai/v1/chat/completions` | `glm-5.3` (alternative `mistral-large-4`) | Prepaid with nonzero per-token pricing, not free |

A failed node fails over to the next one; when all nodes fail the endpoint returns HTTP 503 with `code = LLM_UNAVAILABLE` and a `correlationId`, surfaced in code as `LLMUnavailableException`. The exact responding node is returned as `modelo_usado` in the form `node/model`, and is `none` when no LLM was called. `429` and `5xx` responses and timeouts count as node failures; there is no retry against the same node. Numeric breaker and timeout values and the failure classification are recorded in [ADR-09](ADR-09-circuit-breaker-degraded-mode.md).

This record removes the **Azure GPU requirement** from the ADR-05 chat workload: the first node runs on hardware the Developer/User already owns. It does **not** make the total cost zero — OpenCode inference is prepaid — and it does not verify the Gemini model id, model quality, latency or the clinical evaluation criteria. It also means that nodes 2 and 3 receive the grounded prompt, so the data-residency analysis remains open under OD-5 and CA-G-16. Acceptance of this ADR still depends on the verification section below.

### Implementation status recorded 2026-11-27 (not an acceptance)

- `clinical-assistant-service/clinical-assistant-service/build.gradle.kts:20,52` pins Spring AI 1.1.1 through the BOM, and line 32 includes the pgvector starter.
- The chat chain is implemented with dedicated `WebClient` adapters (`OllamaHttpLLMAdapter`, `GeminiHttpLLMAdapter`, `OpenCodeHttpLLMAdapter`) selected by `rag.llm.chain` and wrapped by `ResilientLLMRouter` with per-node breakers. It does **not** use Spring AI `ChatClient`. This is a deviation from the framework decision as written above. Before acceptance, either the chain is routed through the Spring AI chat abstraction or this ADR is explicitly amended by the Developer/User to allow the dedicated adapters. Neither option is selected here.
- `application.yaml:58-76` uses custom `ollama.*`, `gemini.*` and `opencode.*` keys, not the Spring AI `spring.ai.ollama.*` keys listed above. Those ADR keys come from static artifact inspection; no runtime spike has confirmed them as effective.
- Embeddings are still provided by `FixtureEmbeddingAdapter`; `clinical.vector-store.dimensions: 768` is marked provisional in `application.yaml:35-36`. No real embedding model, final dimension, relevance threshold or `top-k` has been recorded.
- `docker-compose.yml:157-163` defines an optional `ollama` service under the `ollama` profile and defaults the Assistant to `http://ollama:11434`; that is a local/demo path. The C17 record identifies the first node as an already-owned Ollama endpoint reached through Cloudflare Tunnel via `OLLAMA_BASE_URL`. Neither observation records the Azure deployment target.
- No runtime configuration spike, real-provider evaluation, calibration or ADR-11 region/quota/budget evidence has been recorded.

## Consequences

- The vector dimension becomes a fixed constant in the migration; changing the embedding model requires full re-ingestion. The current value of 768 is provisional and not an accepted decision.
- Under the C17 record, GPU capacity is **not** an Azure deployment dependency for the chat node: Ollama runs on hardware the Developer/User already owns and is reached through a Cloudflare Tunnel. The cloud nodes remain external dependencies; if all three nodes fail, the Assistant returns `503 LLM_UNAVAILABLE` per NFR-03 and ADR-09 and Triage is unaffected. Provider availability is recorded as a metric, never as a readiness gate, and LLM-dependent endpoints recover automatically when a provider returns (developer decision, 2026-11-25).
- Only the first chat node is local. Nodes 2 and 3 receive the grounded prompt, so full perimeter containment cannot be claimed; the privacy and retention review under OD-5 / CA-G-16 is pending before clinical use.
- Tests keep a mocked model (DEC-06). Threshold calibration needs the real model in the test environment, not in unit tests.
- If ADR-11 cannot host the workload, this ADR must be updated before any provider route is accepted.
- The framework deviation recorded above must be reconciled before acceptance; the presence of the pgvector starter alone does not make the chat abstraction compliant.

## Risks and mitigations

- **R-03 / R-04 (pgvector blocking, LLM latency and cost):** isolate the synchronous embedding call on a bounded scheduler, use timeouts and the ADR-09 circuit breaker, and keep a mocked model in tests. Measure latency and cost before accepting the route. Clinical text in logs and metric labels is tracked under CA-G-16 and NFR-19.
- **R-05 (version drift):** pin Spring AI 1.1.1 under DEC-12 and run the runtime spike before relying on static configuration findings.
- **R-06 (Azure access or quota):** use the ADR-11 evidence record; do not assume GPU, region or quota.
- **R-10 (relevance and citations):** calibrate threshold and `top-k` per embedding model under CA-G-02 and AMB-16.
- **CA-G-05 (failure classification):** propose `503 LLM_UNAVAILABLE` for open-circuit and timeout cases; leave 429 and 5xx classification to ADR-09.
- **CA-G-22 (readiness):** keep the proposed startup check separate from runtime readiness.
- **OD-5 / CA-G-16 (data residency):** review the grounded prompt sent to the cloud failover nodes before clinical use; no privacy decision is recorded by this ADR.
- **G-19 (test infrastructure):** add Testcontainers and BlockHound before the runtime verification can run. BlockHound execution is currently NOT AUTHORIZED.
- **PROTO-003 ambiguity:** the literal text assigns 5 % to both mild 3–5 % and moderate 5–8 %. The protocol owner must resolve the boundary; no probe at that boundary is used.

## Verification

The following are acceptance gates, not completed evidence. None has been recorded as passed.

- **Runtime configuration spike:** start the pinned Spring AI 1.1.1 Ollama autoconfiguration and confirm the static keys above are effective.
- **Framework reconciliation:** either the three-node chain runs through the Spring AI chat abstraction, or the Developer/User explicitly amends this ADR to keep the dedicated WebClient adapters.
- **TST-RXN embedding control:** BlockHound detects a direct embedding call from a reactor thread and accepts the call through the bounded scheduler, with positive and negative controls. Execution requires separate authorization.
- **TST-RAG:** PROTO-001 and PROTO-002 return the expected content with the mandatory citation. PROTO-003 uses only the unambiguous probes: 4 % mild, 6.5 % moderate, 9 % severe, and 4 % with altered mental status as severe. The proposed pass rule is five out of five runs per probe, fixed before the run.
- **Negative query:** a question without a matching protocol returns the fixed notice with no clinical content (RN-05.3).
- **ADR-11 evidence:** a recorded answer on whether the chosen Azure target can host the workload, including GPU where required, in the target region and within budget.
- **Calibration:** the real embedding model, threshold and `top-k` are recorded before acceptance.
- **Real-provider evidence:** the Gemini model id is verified with Google and the per-node latency and cost are measured; the OpenCode prepaid cost is recorded.
- No ADR in this line is accepted until these verifications pass and the developer approves the result. Consolidation of the two draft documents does not record acceptance.
