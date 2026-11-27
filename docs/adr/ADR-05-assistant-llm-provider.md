# ADR-05: LLM provider, framework and embedding model for the Clinical Assistant

- **Status:** Proposed. Not accepted. This is the canonical ADR-05 since 2026-11-27; `ADR-05-llm-provider-spring-ai.md` is superseded by this document. The C17 chat chain (2026-11-26), the definitive 768-dimension schema, OD-16 prompt privacy, the hybrid implementation and the lexical embedding baseline were recorded on 2026-11-27. Formal acceptance, real-provider availability, cost and the remaining test evidence are still open.
- **Date:** 2026-11-25 (consolidated and amended 2026-11-27)
- **Deciders:** developer; provider chain recorded 2026-11-26, embeddings/privacy/framework amendment recorded 2026-11-27, ADR acceptance still pending verification
- **Scope:** Choice of the chat route, the framework abstraction and the embedding model behind the Clinical Assistant for C-06. This ADR does not choose the Azure compute target (ADR-11), the vector store access model (ADR-04), the breaker values (ADR-09) or the relevance gate and citation validation mechanics (RN-05, R-10); it records the embedding-specific threshold used by those gates.
- **Related:** DEC-06, DEC-07, DEC-12, AMB-11, AMB-15, AMB-16, NFR-01, NFR-03, NFR-14, R-03, R-04, R-05, R-06, R-10, CA-G-01, CA-G-02, CA-G-05, CA-G-16, CA-G-22, TST-RXN, TST-RAG, OD-5 (clinical retention of `EventoHistorico`, unchanged) and OD-16 (prompt privacy, recorded 2026-11-27) in [clinical-assistant-service.md](../services/clinical-assistant-service.md) and [requirements.md](../requirements.md)

## Context

DEC-06 requires the provider to be selected by configuration and mocked in tests. AMB-15 left the provider open. NFR-14 makes Azure mandatory, but the compute target belongs to ADR-11. The chat nodes and the embedding model must be reachable from that target.

Remote providers receive the question and the retrieved fragments on every query. OD-16 permits that route under a zero-PII rule: the grounded prompt contains only the question and internal protocol fragments, never patient identifiers. The question is free text and is not scrubbed by the current code, so the rule is an authoring constraint until a control is implemented. As comparative context only, Azure OpenAI's default abuse monitoring is documented as retaining prompts and responses for about 30 days, with possible human review; opt-out depends on Microsoft approval. This is not the basis of the decision and must be checked against current Microsoft policy before it is cited elsewhere.

The embedding dimension fixes the vector column and the ANN index (CA-G-01, ADR-04). It is now fixed at 768 by Developer/User decision. Changing the embedding model later requires re-ingesting the corpus and a migration. The relevance threshold is calibrated per embedding model (CA-G-02), so a threshold from one model is not valid for another.

Static inspection of Spring AI 1.1.1 artifacts confirms configuration names and defaults, not runtime behavior. The effective runtime keys in the current implementation are the custom `ollama.*`, `gemini.*` and `opencode.*` keys in `application.yaml`; a runtime spike has not confirmed the static `spring.ai.ollama.*` keys as effective.

## Decision drivers

- Keep clinical text inside the agreed perimeter where feasible, and record explicitly when a route sends it outside (OD-16).
- Keep the provider behind a configuration-driven interface with a mock in tests (DEC-06).
- Isolate synchronous vector-store calls from the Netty event loop (NFR-01, DER-06, R-03).
- Fix clinical evaluation criteria and pass rules before any run.
- Keep recorded Developer/User decisions distinct from verified evidence: a recorded value is not a passed gate.

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
| **Spring AI (pinned 1.1.1 under DEC-12)** | Spring reference documents Spring Boot auto-configuration for [Ollama chat](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html) and [embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html), vector-store auto-configuration including pgvector, and opt-in schema initialization in the [upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) | Fits the Boot 3.5 baseline; already pinned by DEC-12; shares the abstractions used by ADR-04 | Current reference artifact names may differ from 1.1.1 and require pinned-artifact verification; pgvector remains JDBC-based under DER-06 | **Proposed** as the framework for the vector layer by DEC-12; the chat chain uses dedicated reactive adapters under the 2026-11-27 amendment below |
| **LangChain4j** | Official docs list [20+ LLM providers, 30+ vector stores, ChatModel/EmbeddingModel, AI Services and RAG building blocks](https://docs.langchain4j.dev/intro/), the [ChatModel API](https://docs.langchain4j.dev/tutorials/chat-and-language-models/), an [Ollama integration](https://docs.langchain4j.dev/integrations/language-models/ollama/), and separate Spring Boot starters for Boot 3 and Boot 4 | Broad provider and store coverage; comparable model and RAG abstractions; has a Boot 3 starter | Not pinned by DEC-12; adding it would create a second framework surface; compatibility with the pinned Boot 3.5/Spring AI 1.1.1 stack is not verified | **Rejected for this scope**: changing the framework exceeds the provider decision in DEC-06 and would need a superseding ADR |

## Decision

**Proposed: Option D for the embedding route**, with the chat route governed by the C17 record below. The implemented interim baseline is the deterministic `LexicalEmbeddingAdapter` recorded below; it is not Option D and does not satisfy the real-model verification gates. The chat model and the embedding model are evaluated separately; they do not have to use the same hardware, latency budget or cost. ADR-11 records whether the chosen compute target can host the required workload in the target region and within quota and budget; ADR-05 uses that record and cannot be accepted without it. A negative record changes this route, not ADR-11 automatically.

Spring AI 1.1.1 is pinned by DEC-12. Static inspection identifies `spring.ai.ollama.base-url`, `spring.ai.ollama.chat.options.model` and `spring.ai.ollama.embedding.options.model` as the primary keys. `spring.ai.ollama.embedding.model` is an alias. `spring.ai.model.chat=ollama` and `spring.ai.model.embedding=ollama` are optional in 1.1.1 because the autoconfigurations use `matchIfMissing = true`; set them explicitly if more than one provider is on the classpath. The removed 1.0 keys `spring.ai.ollama.chat.enabled` and `spring.ai.ollama.embedding.enabled` must not be used. `EmbeddingModel` is the intended production abstraction; the earlier draft's `EmbeddingClient` name and generic `spring.ai.provider` property are not part of this decision. These keys come from static artifact inspection and are not claimed as the effective runtime configuration.

`EmbeddingModel.call()` is synchronous in the inspected 1.1.1 code and uses `RestClient` in blocking mode. A real embedding adapter must isolate that call on a bounded scheduler as in ADR-04. Whether it shares the pgvector scheduler is not decided here.

A startup check of the endpoint and model is proposed as a separate check under CA-G-22. It does not replace the runtime readiness rule in ADR-09, which excludes LLM state from readiness.

### Developer decision recorded 2026-11-26 (C17 — LLM failover chain)

The Developer/User recorded a three-node chat chain, configured by `RAG_LLM_CHAIN` (`rag.llm.chain`):

| Order | Node | Endpoint / source | Model | Cost characterization |
|---|---|---|---|---|
| 1 | `ollama` | Local Ollama exposed through Cloudflare Tunnel via `OLLAMA_BASE_URL` | `llama3.1:8b` | Already-owned hardware; no new Azure GPU spend |
| 2 | `gemini` | Google AI Studio Free Tier, `generateContent` endpoint | `gemini-3.5-flash-lite` | Stable model with free input/output in the [official model page](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite); runtime endpoint/id verification still pending (R-07) |
| 3 | `opencode` | `https://opencode.ai/inference/openai/v1/chat/completions` | `glm-5.3` | Prepaid, nonzero per-token pricing; third and last fallback |

The earlier record also mentioned `mistral:7b` and `mistral-large-4` as alternatives; the IDs above are the configured values and the ones this decision confirms. A failed node fails over to the next one; when all nodes fail the endpoint returns HTTP 503 with `code = LLM_UNAVAILABLE` and a `correlationId`, surfaced in code as `LLMUnavailableException`. The exact responding node is returned as `modelo_usado` in the form `node/model`, and is `none` when no LLM was called. `429` and `5xx` responses and timeouts count as node failures; there is no retry against the same node. Numeric breaker and timeout values and the failure classification are recorded in [ADR-09](ADR-09-circuit-breaker-degraded-mode.md).

This record removes the **Azure GPU requirement** from the ADR-05 chat workload: the first node runs on hardware the Developer/User already owns. It does **not** make the total cost zero — OpenCode inference is prepaid — and it does not verify model quality, latency or the clinical evaluation criteria. Nodes 2 and 3 receive the grounded prompt under the OD-16 zero-PII policy recorded below. Acceptance of this ADR still depends on the verification section.

### Embedding and retrieval parameters (Developer decision recorded 2026-11-27)

| Parameter | Recorded value | Implementation evidence |
|---|---|---|
| Vector dimension | **768, definitive for the pgvector schema** | `VectorStoreProperties.java`, `application.yaml`, `V3__pgvector_evidence_schema.sql` |
| `top-k` | **4** (`DEFAULT_TOP_K`) | `PromptRequest.java`; `RAGRequestDTO.java` always builds the request with that value, so it is not exposed as a request field |
| Base relevance threshold | **0.20**, overridable per request via `umbral_relevancia` | `VectorStoreProperties.java`, `RAGService.java`, `application.yaml` |
| Embedding provider | **`LexicalEmbeddingAdapter` in production**: accent-stripped, stopword-filtered stem overlap hashed into 768 dimensions; no external API key. `FixtureEmbeddingAdapter` is test-only | `RAGConfiguration.java`, `LexicalEmbeddingAdapter.java`, `V6__lexical_seed_embeddings.sql` |
| Candidate real models | `gemini-embedding-2` through `:embedContent` with `output_dimensionality=768`; `nomic-embed-text` (Ollama) remains an alternative. `text-embedding-004` is superseded | `GeminiEmbeddingAdapter` added 2026-11-27; property-gated and not yet calibrated |
| Calibration | Lexical baseline: Q1 ~= 0.29, Q2 ~= 0.31, Q3 <= 0.14; threshold 0.20 with `top-k` 4 | `SeededCorpusRn05Test.java` against the V4/V5 seeded corpus in pgvector |

The earlier proposal of an initial 0.70 threshold is **not adopted**. The 0.20 value is calibrated for the lexical adapter only and is not evidence for a real semantic model; TST-RAG with the real model still requires its own calibration. "Definitive" applies to the schema dimension, not to a claim that the real embedding model is implemented or validated. Changing the model still requires re-ingestion and a migration.

### Gemini embedding amendment (Developer authorization 2026-11-27)

- The current Google REST contract is `POST /v1beta/models/{model}:embedContent` with the `x-goog-api-key` header, `content.parts[].text` and the snake_case field `output_dimensionality: 768`. `gemini-embedding-2` does not accept `task_type`: asymmetric retrieval uses `task: question answering | query: ...` for questions and `title: none | text: ...` for documents. The earlier `text-embedding-004` candidate is superseded; embedding spaces are model-specific, so V4/V6 lexical vectors must not be mixed with vectors from this model.
- `GeminiEmbeddingAdapter` implements the optional `TaskAwareEmbeddingProvider` port. `RAGService`, `RagRetrievalService` and `CorpusIngestionService` use the query/document methods only when the selected provider supports them. Lexical and fixture providers keep the original `embed(String)` path and their deterministic tests.
- `clinical.vector-store.embedding-provider` defaults to `lexical` and can be set to `gemini` with `EMBEDDING_PROVIDER=gemini`. The default is deliberately unchanged: enabling the semantic model without re-embedding every stored fragment would compare vectors from two incompatible spaces. TST-RAG re-embeds the seeded corpus into a disposable pgvector container before measuring anything.
- `.github/workflows/gemini-embedding-verification.yml` is opt-in (`workflow_dispatch`), reads `secrets.GEMINI_API_KEY` inside Actions and uploads `results.json`; the Secret is never interpolated into a `run:` line. Because this repository's default branch is `main`, the dispatch endpoint only exposes the workflow after the file reaches that branch: the recorded run was triggered by a temporary branch-scoped `push` trigger, removed in this commit. No endpoint, latency, cost or threshold result was claimed before run 46045071910 produced it.

**Developer decision (D-RN05-01, 2026-11-27):** the similarity metric is cosine and `top-k=4` is definitive. The base threshold target is **0.65**, but it is a TST-RAG calibration target, not an immediate config change: the current prefix score belongs to `LexicalEmbeddingAdapter`, where 0.65 would reject every clinical query. The runtime keeps 0.20 until a real semantic model passes the five-run gate and records the measured threshold.

### OD-16 — prompt privacy (Developer decision recorded 2026-11-27)

Sending clinical prompts to the cloud nodes (`gemini`, `opencode`) is **approved under a zero-PII rule**. OD-5 is left intact: it remains the open clinical retention period of `EventoHistorico` and is not redefined by this decision.

- **Current behavior:** `GroundedPrompt.build(question, evidence)` sends only the free-text `pregunta` plus the retrieved, numbered protocol fragments ([GroundedPrompt.java](../../clinical-assistant-service/clinical-assistant-service/src/main/java/com/vidacruz/clinical/infrastructure/adapters/GroundedPrompt.java)). `RAGService` embeds the question and passes that same question to the router ([RAGService.java](../../clinical-assistant-service/clinical-assistant-service/src/main/java/com/vidacruz/clinical/domain/services/RAGService.java)).
- **`contexto_paciente`:** validated (stripped; blank becomes `null`; at most 2000 characters) and carried, but **not forwarded** to retrieval or to the LLM ([PromptRequest.java](../../clinical-assistant-service/clinical-assistant-service/src/main/java/com/vidacruz/clinical/domain/models/PromptRequest.java)). It therefore never reaches a cloud node in the current implementation.
- **Rule:** no patient identifiers may be included in the `pregunta` text sent to `gemini` or `opencode`. Prompt content must be limited to symptoms, vital signs, age and internal protocol fragments.
- **Control boundary:** the code strips and length-checks inputs but has no PII detector or scrubber. Compliance is a prompt-authoring constraint, not an automated guarantee; `toString()` deliberately omits free text from logs (CA-G-16).
- **Decision (OD-16, 2026-11-27):** keep authoring control plus wire-level payload tests; **no automatic regex scrubber**. An automated scrubber could mutilate legitimate clinical terms. Adding one would require a separate decision. OD-5 remains a distinct 5-year retention decision for `EventoHistorico`.

### Hybrid implementation — Spring AI vector layer + reactive WebClient chat (Developer amendment recorded 2026-11-27)

The Developer/User explicitly amends the framework route above to keep the dedicated reactive adapters. This closes the framework-reconciliation gate by decision, not by test evidence.

| Layer | What is actually in place | Why |
|---|---|---|
| Vector persistence and retrieval | `springAiVersion = 1.1.1` with `spring-ai-starter-vector-store-pgvector`. Flyway owns the schema through migrations V3/V4. The active adapter is `PgvectorVectorStoreAdapter` over the repository's own `VectorRepository` (`JdbcClient`); synchronous JDBC calls run inside `Mono.fromCallable(...).subscribeOn(pgvectorJdbcScheduler)` on the dedicated `Schedulers.newBoundedElastic(4, 100, "pgvector-jdbc")` | Keeps pgvector behind the reactive `VectorStore` port and off the Netty event loop (NFR-01, ADR-04). Using custom JDBC rather than `PgVectorStore` is an implementation detail of that adapter |
| Embeddings | The production bean is property-gated: `LexicalEmbeddingAdapter` by default, `GeminiEmbeddingAdapter` when `clinical.vector-store.embedding-provider=gemini`; `FixtureEmbeddingAdapter` remains test-only | Deterministic stem-overlap retrieval with no network or API key stays the default until the corpus is re-embedded; the Gemini call is non-blocking `WebClient`, with no Spring AI `EmbeddingModel` or `call()` on the reactor path |
| Chat inference | `OllamaHttpLLMAdapter`, `GeminiHttpLLMAdapter` and `OpenCodeHttpLLMAdapter` implement `NamedLLMProvider` with `Mono<String>` and share `GroundedPrompt`. `ResilientLLMRouter` returns `LLMCompletion` and protects each provider with `Mono.timeout(...)` plus Resilience4j `CircuitBreakerOperator` and metrics; no `ChatClient` and no `TimeLimiterOperator` | Chosen over Spring AI `ChatClient` to avoid heterogeneous-provider autoconfiguration collisions and keep exact control over each provider's JSON payload, headers and timeouts while staying non-blocking. `ChatClient.call()` is synchronous, but `ChatClient.prompt().stream().content()` returns a `Flux<String>`; "ChatClient is always blocking" is therefore **not** the reason for this amendment |
| Domain | `RAGService`, ports and models have no Spring AI or provider SDK dependency | Clean architecture: adding or replacing a chain node does not change clinical logic or the RN-05 gates |

This is a design intent to avoid blocking on the event loop, not a claim that BlockHound has passed. TST-RXN remains pending and BlockHound execution is not authorized.

### Implementation status recorded 2026-11-27 (not an acceptance)

- `clinical-assistant-service/clinical-assistant-service/build.gradle.kts:20,35,55` pins Spring AI 1.1.1 through the BOM and includes the pgvector starter.
- The chat chain is implemented with dedicated `WebClient` adapters selected by `rag.llm.chain` and wrapped by `ResilientLLMRouter` with per-node breakers and timeouts; it does **not** use Spring AI `ChatClient`. The 2026-11-27 amendment above records this as the accepted development route for this ADR draft.
- `application.yaml:59-80` uses custom `ollama.*`, `gemini.*` and `opencode.*` keys, not the Spring AI `spring.ai.ollama.*` keys listed above. Those ADR keys come from static artifact inspection; no runtime spike has confirmed them as effective.
- Embeddings in production default to `LexicalEmbeddingAdapter`; V6 updates the V4 seed vectors to that adapter. The 768 schema dimension is definitive; `top-k` is 4 and the calibrated lexical threshold is 0.20. `GeminiEmbeddingAdapter` is wired but opt-in, and no semantic model has been calibrated.
- `docker-compose.yml:166-175` defines an optional `ollama` service under the `ollama` profile and defaults the Assistant to `http://ollama:11434`; that is a local/demo path. The C17 record identifies the first node as an already-owned Ollama endpoint reached through Cloudflare Tunnel via `OLLAMA_BASE_URL`. Neither observation records the Azure deployment target; ADR-11 now records the AKS parameters.
- No runtime configuration spike, real-provider evaluation, BlockHound run, TST-RAG calibration or R-06 repository artifact has been recorded.

## Consequences

- The vector dimension is fixed at 768 in the schema; changing the embedding model requires full re-ingestion and a migration. This is a recorded decision, not evidence that a real embedding model works.
- Under the C17 record, GPU capacity is **not** an Azure deployment dependency for the chat node: Ollama runs on hardware the Developer/User already owns and is reached through a Cloudflare Tunnel. The cloud nodes remain external dependencies; if all three nodes fail, the Assistant returns `503 LLM_UNAVAILABLE` per NFR-03 and ADR-09 and Triage is unaffected. Provider availability is recorded as a metric, never as a readiness gate, and LLM-dependent endpoints recover automatically when a provider returns (developer decision, 2026-11-25).
- Only the first chat node is local. OD-16 authorizes nodes 2 and 3 to receive the grounded prompt under the zero-PII rule; `contexto_paciente` is not forwarded. Full perimeter containment is not claimed, and the free-text question remains the residual PII surface until an automated control exists. OD-5 is unchanged.
- Tests keep a mocked model (DEC-06). The lexical threshold is calibrated against the seeded corpus with a deterministic adapter; a real model still needs its own calibration in the test environment.
- The framework deviation is resolved by the recorded amendment; it does not waive TST-RXN.
- If ADR-11 cannot host the workload, this ADR must be updated before any provider route is accepted.

## Risks and mitigations

- **R-03 / R-04 (pgvector blocking, LLM latency and cost):** pgvector JDBC calls are isolated on a bounded scheduler; a real embedding call must also be isolated. Use timeouts and the ADR-09 circuit breaker, and keep a mocked model in tests. Measure latency and cost before accepting the route. Clinical text in logs and metric labels is tracked under CA-G-16 and NFR-19.
- **R-05 (version drift):** pin Spring AI 1.1.1 under DEC-12 and run the runtime spike before relying on static configuration findings.
- **R-06 (Azure access or quota):** ADR-11 now records AKS and its SKU/region values as Developer/User-validated; the repository still has no R-06 evidence artifact. Do not treat the amendment as that artifact.
- **R-07 (Gemini runtime verification):** `gemini-3.5-flash-lite` is the stable, active replacement recorded by the Developer/User and is available in the Google AI Studio Free Tier ([official model page](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite)); the earlier `gemini-2.5-flash` deprecation caveat no longer applies to the configured model. The endpoint and id still have to be verified at runtime, and latency and cost measured, before acceptance.
- **R-10 (relevance and citations):** calibrate threshold and `top-k` per embedding model under CA-G-02 and AMB-16; 0.20 is calibrated for the lexical baseline only and must be recalibrated for a real semantic model.
- **CA-G-05 (failure classification):** `503 LLM_UNAVAILABLE` for open-circuit and timeout cases; 429 and 5xx classification is in ADR-09.
- **CA-G-22 (readiness):** keep the proposed startup check separate from runtime readiness.
- **OD-16 / CA-G-16 (prompt privacy):** send only question plus retrieved fragments to cloud nodes; never include patient identifiers in `pregunta`. By developer decision there is **no automated scrubber**: authoring control plus payload tests are the accepted control. OD-5 is a separate 5-year retention decision and is unaffected.
- **G-19 (test infrastructure):** Testcontainers is present; BlockHound `1.0.10.RELEASE` plus `blockhound-junit-platform` is authorized and pending execution (TST-RXN).
- **PROTO-003 ambiguity:** the literal text assigns 5 % to both mild 3–5 % and moderate 5–8 %. The 5 % boundary is documented as a literal overlap in the source protocol, not used as a probe and not resolved by inventing a clinical rule. Only unambiguous probes are used: 4 % mild, 6.5 % moderate, 9 % severe, and 4 % with altered mental status as severe.

## Verification

The following are acceptance gates, not completed evidence. None has been recorded as passed. The framework-reconciliation item was closed by the recorded developer amendment on 2026-11-27, which is a decision and not a test result.

- **Runtime configuration spike:** start the pinned stack and confirm that the custom `ollama.*`, `gemini.*` and `opencode.*` keys are effective; the `spring.ai.ollama.*` keys remain static-inspection findings only.
- **Framework reconciliation:** closed by the 2026-11-27 amendment that keeps the dedicated `WebClient` adapters. Reopen only if the runtime spike contradicts the non-blocking design.
- **TST-RXN:** BlockHound detects a blocking call made directly from a reactor thread and accepts the pgvector path through the bounded scheduler, with positive and negative controls, covering both vector retrieval and the WebClient chain. Execution is authorized with `1.0.10.RELEASE` + `blockhound-junit-platform`; the gate stays open until the negative control is observed and both suites pass.
- **TST-RAG:** PROTO-001 and PROTO-002 return the expected content with the mandatory citation. PROTO-003 uses only the unambiguous probes: 4 % mild, 6.5 % moderate, 9 % severe, and 4 % with altered mental status as severe. The proposed pass rule is five out of five runs per probe, fixed before the run. `SeededCorpusRn05Test` records the deterministic lexical-baseline outcomes for Q1, Q2 and the diabetes negative query; it does not close the five-run real-provider gate.
- **Negative query:** a question without a matching protocol returns the fixed notice with no clinical content (RN-05.3); the seeded-corpus test also asserts that no LLM node is called.
- **ADR-11 evidence:** repository artifact showing that the recorded AKS target can host the workload in `northcentralus` within the Azure for Students budget; the Developer/User validation record is not a substitute for that artifact.
- **Calibration:** 0.20 and `top-k` 4 are recorded for the lexical adapter from the seeded corpus. For the real model (`gemini-embedding-2`, 768 dimensions, task prefixes), TST-RAG run 46045071910 confirms `top-k=4` and the 0.65 target: minimum positive top score 0.7473 versus negative top score 0.5842. The runtime threshold remains 0.20 because production still defaults to the lexical adapter and the seed vectors have not been re-embedded; switching models requires full re-ingestion.
- **Real-provider evidence:** verify the `gemini-3.5-flash-lite` endpoint and id at runtime for the account; measure per-node latency and cost and record the OpenCode prepaid cost.
- **OD-16 control review:** confirm in tests that `contexto_paciente` never reaches the LLM payload and that no patient identifiers appear in the `pregunta` sent to cloud nodes. The decision is recorded: no automated scrubber; authoring control and wire-level tests are the accepted boundary. Reopen only if that evidence fails.
- No ADR in this line is accepted until these verifications pass and the developer approves the result. Consolidation of the two draft documents does not record acceptance.

### Gate evidence status (2026-11-27)

- **OD-16 control review: PASS.** `CloudPromptPrivacyTest.geminiPayloadContainsOnlyTheQuestionAndRetrievedEvidence` and `opencodePayloadContainsOnlyTheQuestionAndRetrievedEvidence` assert on the JSON body captured at the wire for both real HTTP adapters against a local stub; neither `contexto_paciente` nor the patient id is present. No automated scrubber was added, per OD-16. This closes only the OD-16 gate, not TST-RAG.
- **TST-RAG: PASS (2026-11-27).** The opt-in run [46045071910](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46045071910) consumed `secrets.GEMINI_API_KEY` inside Actions and executed `GeminiEmbeddingVerificationTest` against `gemini-embedding-2` (768 dimensions, `top-k=4`, target `0.65`). The uploaded `results.json` records 5/5 passing runs for each of six probes: PROTO-001 (top score 0.8265), PROTO-002 (0.7473), and the four unambiguous PROTO-003 probes (0.8367–0.8405). The negative diabetes query scored 0.5842, returned the fixed notice and made no LLM call. Measured embed+search latency was 198–353 ms over 30 runs; 37 embedding requests and 3,687 input characters were counted. Cost is recorded as **not measured** (no billing API queried). Wire-level `GeminiEmbeddingAdapterWireTest` passed 3/3 locally against a stub. The LLM in this run is a stub, so the chat route remains unverified; the earlier local Ollama pull timeout and the chat-only composed profile are unchanged findings.
- **Real-provider evidence: PARTIAL.** The embedding endpoint/model id and its latency were verified in run 46045071910; cost was not measured. The `gemini-3.5-flash-lite` **chat** endpoint/id, per-node latency and cost remain unverified, so R-07 stays open.
- **Runtime configuration spike: BLOCKED.** No running stack; the custom key names are confirmed only by static inspection of `application.yaml` and the adapters.
- **ADR-11 evidence: BLOCKED.** No Azure artifact exists for the North Central US AKS sizing or the Azure for Students budget; the Developer/User validation record is not a substitute.
- **TST-RXN: PASS.** BlockHound `1.0.10.RELEASE` + `blockhound-junit-platform` are wired in both services with Testcontainers exceptions; the negative control asserts `hasRootCauseInstanceOf(BlockingOperationError.class)` and passed in each module. Full suites rerun per work unit on 2026-11-27: `feat/rn05-grounded-answer-contract` clinical 46 suites / 229 tests; `fix/blockhound-outbox-nonblocking` clinical 44 suites / 219 tests and triage 32 suites / 144 tests; 0 failures and 0 errors in all four runs. GitHub Actions reran both modules on Ubuntu after the Linux-entropy portability fix (run [46032155156](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46032155156), success). The Kafka outbox send needed `subscribeOn(Schedulers.boundedElastic())` because `KafkaTemplate.send()` blocks on metadata before returning its future. This closes TST-RXN only; it does not close TST-RAG, R-07, the runtime spike or the ADR-11 artifact.
- Formal acceptance of ADR-05 remains `Proposed` until TST-RAG, runtime spike, real-provider evidence and ADR-11 artifact are supplied and approved.
