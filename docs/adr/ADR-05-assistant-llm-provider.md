# ADR-05: LLM provider and embedding model for the Clinical Assistant

- **Status:** Proposed. Not accepted. The provider route is proposed. The hardware, the model and the dimension are pending the technical evaluation in §6 and §7. Viability depends on ADR-11. Nothing here is a final commitment.
- **Scope:** Choice of the chat model and the embedding model used behind Spring AI. The framework choice (Spring AI 1.x) comes from DEC-06 (Proposed). This ADR does not reopen that proposal.
- **Related:** DEC-06, DEC-07, AMB-15, NFR-01, NFR-03, NFR-14, R-04, CA-G-01, CA-G-02, CA-G-05, CA-G-16, CA-G-22 in [clinical-assistant-service.md](../services/clinical-assistant-service.md). ADR-04 (vector dimension and index). ADR-09 (circuit breaker and failure response). ADR-11 (Azure compute choice, which decides whether Option D is viable). Gate identifiers with the `CA-` prefix (`CA-G-*`) refer to the gap list of the Clinical Assistant service document, not to the Triage gaps with the same numbers.

## 1. Context

DEC-06 requires the provider to be selected by configuration, with a mock in tests. AMB-15 left the provider open. This ADR records only the provider route and the embedding model.

Four facts constrain the choice:

1. **Azure is a requirement, not an option.** NFR-14 requires an Azure target (AKS or Container Apps). The compute choice belongs to ADR-11, not here. The chat model and the embedding model must be reachable from the Azure deployment.
2. **Remote providers receive clinical text on every query.** The question and the retrieved fragments are sent to the provider. As comparative context only, Azure OpenAI's default abuse monitoring is documented as retaining prompts and responses for about 30 days, with possible human review. Opting out requires Microsoft approval for eligible customers ([Limited access](https://learn.microsoft.com/en-us/legal/cognitive-services/openai/limited-access), [Abuse monitoring](https://learn.microsoft.com/en-us/azure/ai-foundry/openai/concepts/abuse-monitoring)). This is not the basis of the decision and must be checked against the current Microsoft policy before it is cited anywhere else.
3. **The embedding dimension fixes the vector column and the ANN index** (CA-G-01, ADR-04). Changing the embedding model later requires re-ingesting the whole corpus and a migration.
4. **The relevance threshold is calibrated per embedding model** (CA-G-02). A threshold calibrated with one model is not valid for another.

## 2. Options

| Option | Pros | Cons |
|---|---|---|
| **A. Azure OpenAI (chat and embeddings)** | Fits NFR-14. One vendor for chat and embeddings. | Default retention and possible human review are documented (§1, point 2). Opt-out depends on Microsoft approval. |
| **B. OpenAI API (direct)** | Simple setup. | Data leaves the Azure perimeter. Terms for clinical data not reviewed. |
| **C. Anthropic API (chat) with a separate embedding provider** | Strong chat model. | Two vendors, two sets of terms, keys and failure modes. |
| **D. Local model served by Ollama** | Chat and embedding models run inside the agreed Azure perimeter, so the question and fragments are not sent to an external provider. No per-call vendor cost. | Requires GPU capacity in Azure, which depends on ADR-11. Model quality for protocols must be evaluated. Latency must be measured. |

## 3. Proposed route

**Option D: Ollama, served inside the Azure environment.** This is the route proposed for evaluation. It is not accepted. It is not viable until ADR-11 demonstrates the GPU requirement (§4). The decision is also conditional on the technical evaluation in §6 and §7, the clinical evaluation in §8, and the budget and quota in §9 (decision 3).

## 4. Infrastructure

- **Azure is mandatory** (NFR-14). The model is served inside the Azure deployment.
- **Production:** GPU, as a **provisional requirement**. It is not a hardware specification and it is not a spending commitment.
- **Development:** CPU is allowed for functional tests only. CPU is not accepted as production architecture unless measurements show that the latency objectives are met.
- **Chat and embeddings are evaluated separately.** Each one has its own model, hardware need, latency, and cost. They must not be assumed to need the same hardware.
- **Viability dependency on ADR-11.** ADR-11 must show that the chosen Azure compute target (AKS or Container Apps) can host Ollama with the GPU required, in the target region, within quota and budget. This is **not assumed** for either option: GPU availability, regional availability, quotas and cost may differ between them. Option D is not considered viable until this is verified.

## 5. Spring AI 1.1.x configuration (static verification against 1.1.1 artifacts; runtime verification pending)

The keys below were checked in the configuration metadata (`spring-configuration-metadata.json`) and in the source of `spring-ai-autoconfigure-model-ollama`, `spring-ai-ollama` and `spring-ai-model` 1.1.1, downloaded from Maven Central. The check is static: it confirms names and default behavior, not runtime behavior. The effective configuration of the application and its behavior at runtime remain pending the spike.

- Starter: `org.springframework.ai:spring-ai-starter-model-ollama`, for both chat and embeddings. The version is managed by the Spring AI BOM.
- Base URL: `spring.ai.ollama.base-url`. Static inspection, Spring AI 1.1.1.
- Chat model: `spring.ai.ollama.chat.options.model`. Static inspection, Spring AI 1.1.1.
- Embedding model: `spring.ai.ollama.embedding.options.model`. Static inspection, Spring AI 1.1.1. The property `spring.ai.ollama.embedding.model` is an alias that delegates to the same option. It is recorded here for recognition only; the primary key is `options.model`.
- Model type selectors: `spring.ai.model.chat=ollama` and `spring.ai.model.embedding=ollama`. In 1.1.1 these are **optional**: both Ollama autoconfigurations use `matchIfMissing = true`, so Ollama is activated when the property is absent. Configure them explicitly when more than one provider is present on the classpath.

The 1.0 reference pages show the same keys, as reference documentation; they were not verified against 1.0 artifacts. Do not reuse examples from other lines without checking them. The 1.0 reference also documents `spring.ai.ollama.chat.enabled` and `spring.ai.ollama.embedding.enabled` as removed. Do not use them.

Note: the static inspection above was performed against the Spring AI 1.1.1 artifacts downloaded from Maven Central (spring-ai-autoconfigure-model-ollama, spring-ai-ollama and spring-ai-model, with sources). It has not been executed at runtime. The effective configuration of the application and its runtime behavior remain pending the spike.

## 6. Embedding execution model (static source inspection; runtime verification pending)

The reference pages do not state whether the calls are blocking or non-blocking, except that the chat page shows streaming through `Flux`. The 1.1.1 source indicates the answer for embeddings.

- **`EmbeddingModel.call()` is a synchronous call through `RestClient`.** `OllamaEmbeddingModel.call()` calls `OllamaApi.embed()` directly and returns an `EmbeddingResponse`, with no `Mono` or `Flux`. `OllamaApi.embed()` uses `RestClient` in blocking mode. This is established by static inspection of the 1.1.1 code. It is not demonstrated at runtime.
- **The embedding path has no reactive isolation or scheduler hop.** In the `spring-ai-ollama` 1.1.1 sources inspected, the only `Schedulers.boundedElastic()` is in the chat streaming path. The embedding call therefore runs on the calling thread.
- **The same control applies to the embedding call on the request path.** Converting the question to a vector happens before retrieval. ADR-04 measures only the pgvector search, so the spike must include this call as well.
- **The design must isolate the embedding call on a bounded scheduler,** following ADR-04's isolation design, so that it does not block the event loop. Whether it uses the same scheduler as the pgvector adapter or a separate one is not decided here.
- **BlockHound detection is still pending.** The spike must show that BlockHound detects the call at runtime, with a positive and a negative control as in ADR-04 (AC-2). Until then, the static reading is not a runtime proof.

## 7. Dimension and index (technical evaluation, coordinated with ADR-04)

The embedding dimension is pending the technical evaluation in this section. It is decided together with ADR-04's index type and distance metric, because both affect storage and retrieval performance. The evaluation measures, for each model separately, latency, hardware use and cost. The procedure and its acceptance criteria are fixed before the run.

## 8. Clinical evaluation (before any acceptance)

The evaluation uses the protocols in the corpus. The three acceptance questions of D1 §12 are fixed. PROTO-003 is a corpus protocol included in D1 (`docs/requirements.md` §12, protocol corpus section). It is not one of the three acceptance questions and does not replace or alter them.

For each case, the criteria are fixed before the run: the exact question, the expected content, the mandatory citation, the criteria for an incorrect answer, and the criteria for an insufficient answer.

| Protocol | Question | Expected content (from the corpus) | Mandatory citation | Incorrect | Insufficient |
|---|---|---|---|---|---|
| **PROTO-001** | "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?" | Re-check BP after 5 minutes of rest; do not reduce BP more than 25 % in the first hour; monitor for encephalopathy | PROTO-001 | Any measure not in the protocol, or a contraindicated measure presented as recommended | Omits any of the three listed measures |
| **PROTO-002** | "¿Cuándo se administra el primer antibiótico en sepsis?" | Lactate and blood cultures before the antibiotic; broad-spectrum antibiotic in the first hour | PROTO-002 | Antibiotic timing presented as later than the first hour, or before cultures are taken | Omits the first-hour timing or the cultures |
| **PROTO-003** (corpus protocol, D1) | "¿Cómo se clasifica la deshidratación pediátrica según la pérdida de peso?" | Only the unambiguous probes in the table below | PROTO-003 | Any category assigned to the wrong range for a probe | Omits the category or, for severe, the altered-mental-status criterion |

**Full text of PROTO-003 in D1 §12 (protocol corpus section), quoted literally:** "Classification by weight loss: mild 3–5%, moderate 5–8%, severe > 8% or altered mental status. Rehydration: mild or moderate without vomiting → oral 50 ml/kg over 4 h; severe → IV bolus 20 ml/kg."

**Source ambiguity in PROTO-003.** The literal text assigns the value 5 % to both "mild 3–5 %" and "moderate 5–8 %". The boundary therefore has no unambiguous category. This ADR does not choose a category for it. The protocol owner must decide it, and the corpus must be corrected before any probe at that boundary is included.

**Unambiguous probes for PROTO-003 (proposed, pending approval):**

| Probe (weight loss) | Expected category | Source of the expected category |
|---|---|---|
| 4 % | Mild | 3–5 % range |
| 6.5 % | Moderate | 5–8 % range, excluding the boundary |
| 9 % | Severe | > 8 % range |
| 4 % with altered mental status | Severe | "severe > 8 % or altered mental status": altered mental status is a severe criterion independent of the weight-loss range. This probe checks that the criterion is applied even when the weight loss is in the mild range |

**Proposed pass rule for PROTO-003 (pending approval before the run):** the answer is correct and cited in five out of five runs for each probe, with the same model configuration. The rule for PROTO-001 and PROTO-002 is set the same way before their runs.

Negative queries (no matching protocol) must return the fixed notice with no clinical content (RN-05.3, D1 §12).

## 9. Decisions pending

1. Chat model name, with the hardware it needs.
2. Embedding model name, with its dimension, coordinated with ADR-04.
3. GPU budget and quota in the Azure subscription (R-06, AMB-11). Not confirmed.
4. ADR-11: demonstration that the chosen Azure compute target can host Ollama with the required GPU, in the target region, within quota and budget.
5. Verification of the current retention and data-processing terms of external providers (§1, point 2). This is a policy check, not an architecture decision.
6. Whether the local route is the only one that meets the project's privacy and data-residency requirements. This depends on decision 5.
7. Relevance threshold and top-k, calibrated with the chosen embedding model (CA-G-02, AMB-16).
8. Timeouts and circuit breaker values for the model (CA-G-05, ADR-09).
9. Approval of the pass rule and of the criteria for PROTO-001, PROTO-002 and PROTO-003 before any run.
10. Approval of the failure response in §11.
11. Resolution of the 5 % boundary in PROTO-003 by the protocol owner, with the corpus corrected in D1 §12.

## 10. Privacy scope

Under Option D, the statement "no clinical text leaves the Azure environment through the model call" holds only for **the chat and embedding models running inside the agreed Azure perimeter**. It does not cover logs, telemetry, traces or auxiliary external services, which can still receive clinical text. Those need the controls of CA-G-16, which are not decided here.

## 11. Failure response (to coordinate with NFR-03, ADR-09, CA-G-05 and CA-G-22)

The scenarios below are **representative, not a complete classification**. NFR-03 defines only the response `503 LLM_UNAVAILABLE` for a circuit that is open or a timeout. CA-G-05 also lists 5xx responses and 429 responses as candidate failure types. A busy GPU endpoint may return one of them. The complete classification of failure types is pending CA-G-05 and ADR-09. The rows below are proposals and are not decided here.

| Scenario | Detection | Proposed response | Status |
|---|---|---|---|
| Model not available at startup (endpoint unreachable or model not present) | Startup check of the endpoint and the model | Service not ready. Questions are not served. The event is logged | Proposed. Coordinate with CA-G-22 (readiness) and ADR-09 |
| Model fails during a request (connection refused or dropped) | Client error on the call | `503 LLM_UNAVAILABLE`. Counted by the circuit breaker | Proposed. Pending approval in ADR-09 and coordination with CA-G-05. Not attributed to NFR-03 until its scope is expressly extended |
| GPU saturated or model queue full | Busy response or latency beyond the timeout | Counted as a timeout. `503 LLM_UNAVAILABLE` after the timeout | Proposed. Whether a 429 or a 5xx is returned must be verified with Ollama. Pending CA-G-05 |
| Provider timeout | Timeout reached (CA-G-05) | `503 LLM_UNAVAILABLE` | Pending CA-G-05 |

No code other than `LLM_UNAVAILABLE` is defined for these scenarios. Whether a distinct code is needed is an open decision for ADR-09.

## 12. Consequences

- The vector dimension is a fixed constant in the migration. Changing the embedding model requires full re-ingestion.
- Option D is not viable until ADR-11 shows that the Azure target can run the required GPU workload.
- Under Option D, the model calls stay inside the Azure perimeter (§10). Logs, telemetry and traces still need CA-G-16.
- Under Option D, GPU capacity becomes a deployment dependency. If it is not available, the Assistant returns 503 per §11. Triage is unaffected.
- Tests keep using a mock model (DEC-06). Threshold calibration needs the real embedding model and runs in the test environment, not in unit tests.
