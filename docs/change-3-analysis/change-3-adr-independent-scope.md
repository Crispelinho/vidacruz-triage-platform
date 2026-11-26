# Change 3 (Clinical Assistant RAG): ADR-independent scope

Status: scaffold complete for everything that does not depend on ADR-11, ADR-05 or ADR-09. Nothing here simulates an
accepted decision. Where a document elsewhere in the repo states that ADR-05 is "DECIDED"
(`change-3-dependencies-updated.md`), this change treats ADR-05 as NOT accepted, per the current instruction.

## 1. Real vs fixture vs placeholder

| Piece | Class | Status | Notes |
|-------|-------|--------|-------|
| `PromptRequest`, `RAGResponse`, `EvidenceFragment`, `Citation` | domain model | REAL | Invariants enforced, tested. |
| `RAGService` pipeline, `CitationValidator`, `EvidenceContext` | domain service | REAL | Provider-agnostic logic. |
| `VectorStore`, `EmbeddingProvider`, `LLMProvider` | port | REAL | Contract tests exist (section 3). |
| `PgvectorVectorStoreAdapter` + `VectorRepository` + Flyway `V3` | adapter | REAL | Verified against pgvector via Testcontainers. Dimension 768 is PROVISIONAL (ADR-05). |
| `FixtureVectorStoreAdapter` | adapter | FIXTURE | In-memory exact cosine. Selected with `clinical.vector-store.mode=fixture`. Not evidence that pgvector retrieval quality is adequate. |
| `FixtureEmbeddingAdapter` | adapter | FIXTURE | Hash-seeded unit vectors, no semantics. Replaced when ADR-05 picks the embedding model. |
| `OllamaLLMAdapter` | adapter | FIXTURE (named for its future role) | Never calls the network; canned answer with valid `[n]` markers. Model name `llama3.1:8b` is provisional. |
| `Resilience4jConfiguration` circuit breaker | config | PLACEHOLDER (passive) | Bean exists, nothing wraps the LLM. Thresholds are provisional defaults (ADR-09 / G-05). |
| Model B fallback | - | NOT IMPLEMENTED | No port, no stub. Out of scope until ADR-09. No equivalence between models is claimed. |
| `POST /rag` | API | PROVISIONAL path | Final path is `POST /asistente/consulta` (API-04), plus authentication (ADR-07), both outside this scope. |

## 2. Port contracts

```java
interface EmbeddingProvider { Mono<float[]> embed(String text); }

interface VectorStore {
    Mono<EvidenceFragment> save(EvidenceFragment f);              // upsert by id
    Mono<EvidenceFragment> findById(UUID id);                     // empty when absent
    Mono<Boolean>          deleteById(UUID id);                   // false when absent
    Mono<List<EvidenceFragment>> similaritySearch(float[] q, int topK); // closest first, empty on empty corpus
}

interface LLMProvider { Mono<String> generate(String question, List<EvidenceFragment> evidence); }
```

- Naming: the requested `search(embedding, topK)` already exists as `similaritySearch`; it was not renamed to avoid
  churn in code and tests.
- Citation protocol: the answer cites evidence with `[n]`, `n` being the 1-based position in the evidence list given to
  the LLM. `EvidenceContext.render` produces that numbered context; `CitationValidator.resolve` rejects any marker that
  is `[0]` or beyond the evidence size (`InvalidCitationException`, HTTP 502 `INVALID_LLM_RESPONSE`). An answer without
  markers is valid. Citations are never added or repaired, and `Citation` can only be built from a real fragment.
- A real `LLMProvider` must (a) honor that marker protocol, (b) fail with `LLMUnavailableException`. The contract tests
  encode both.

## 3. HTTP contract (`POST /rag`)

Request: `pregunta` (required, max 2000 chars), `contexto_paciente` (optional, max 2000), `umbral_relevancia`
(optional, 0..1).
Response 200: `respuesta`, `fuentes[{protocolo, seccion}]` (API-04 shape), `citas[{posicion, fragmentoId, documentoId,
indiceFragmento, protocolo, seccion}]` (additive). No embeddings, no chunk text, no confidence field.

| Condition | Status | `code` |
|-----------|--------|--------|
| Invalid question / context / threshold | 400 | `INVALID_REQUEST` |
| LLM timeout or unavailable | 503 | `LLM_UNAVAILABLE` |
| Answer cites unretrieved evidence | 502 | `INVALID_LLM_RESPONSE` |

Error body: `code`, `message`, `correlationId`. The existing `timestamp` expectation from the brief is not implemented:
API-04 / NFR-20 define `correlationId`, which was kept.

### Accepted but NOT acted on (explicit)

- `contexto_paciente`: validated and carried in `PromptRequest`; not sent to retrieval or the LLM. Whether patient data
  may reach a model is a privacy/ADR decision (CA-G-16).
- `umbral_relevancia`: validated and carried; not enforced. The ports return no similarity score, and score semantics
  depend on the embedding model (ADR-05). Clients must not assume it filters results.
- Confidence: deliberately absent. An LLM does not yield a calibrated clinical probability.

## 4. Test matrix

| Area | What is tested | Where |
|------|----------------|-------|
| Domain models | invariants, bounds, privacy of `toString` | `domain/models/*Test` |
| Citations | provenance mapping, marker validation, context rendering | `CitationTest`, `CitationValidatorTest`, `EvidenceContextTest` |
| Pipeline (fixture to fixture) | ranking, top-K, empty corpus, citation-to-store traceability, rejection of invented citation, coarse latency guard | `integration/FixtureRagPipelineTest` |
| Port contracts | `VectorStore` on fixture AND real pgvector; `EmbeddingProvider`, `LLMProvider` on fixtures | `contract/*` |
| HTTP | success, citations, optional fields, 400/502/503 | `RAGControllerTest`, `RAGControllerApiContractTest` |
| Persistence | Flyway V3, `vector(768)`, HNSW cosine index, SQL round trips | `VectorRepositoryTest`, `ClinicalSchemaIsolationTest` |
| Isolation | assistant DB holds only its own tables; config and migration never mention the triage DB | `ClinicalSchemaIsolationTest` |
| Wiring | store selection by property, defaults, fail-fast properties | `RAGConfigurationTest`, `FixtureModeWiringTest` |

Postponed (needs an accepted ADR): real Ollama calls, model B fallback, circuit breaker behavior and thresholds,
relevance-score thresholds, embedding quality or recall, performance against a real model.

Isolation limits: there is no cross-database code path to test because the service has a single datasource. Compose
names the databases `triage-db` and `assistant-db` (not `clinical_db`); the checks above are structural, not a proof of
network-level separation.

The latency test is a coarse regression guard (average under 500 ms on a 200-fragment in-memory corpus), not a
performance claim.

## 5. ADR dependency table

| Item to connect | Blocked by | What changes when accepted |
|-----------------|------------|----------------------------|
| Real LLM (Ollama) | ADR-11 (capacity) then ADR-05 (provider/model) | New `LLMProvider` adapter; keep contract tests; drop fixture latency. |
| Real embedding model and final dimension | ADR-05 | New `EmbeddingProvider`; new Flyway migration (`vector(N)`) and full re-ingestion; update `clinical.vector-store.dimensions`. |
| Relevance threshold | ADR-05 (score semantics) | Add scores to the retrieval port, enforce `relevanceThreshold`. |
| Circuit breaker wiring and thresholds | ADR-09 (after ADR-05) | Wrap `LLMProvider`, map open circuit to `LLMUnavailableException`, replace provisional values. |
| Model B fallback | ADR-09 equivalence validation | New adapter and selection policy. Nothing exists today. |
| Patient context to the model | privacy decision (CA-G-16) | Extend `LLMProvider` input. |
| Final endpoint and auth | API-04 / ADR-07 | Move `/rag` to `/asistente/consulta`. |

Ready-to-connect criterion: replacing the three fixture adapters (embedding, LLM, plus the store already real) with real
providers requires no change to `RAGService`, the ports or the API; only wiring in `RAGConfiguration`.

## 6. Progress report (2026-11-26)

- Baseline found: 71 tests, all passing (once run from an ASCII path, see 7).
- Now: 136 tests, 0 failures, 0 skipped (Docker was available, so Testcontainers suites ran).
- Added: `Citation`, `CitationValidator`, `EvidenceContext`, `InvalidCitationException`, `FixtureVectorStoreAdapter`,
  optional request fields, `citas` in the response, 502 mapping, `clinical.vector-store.mode`, contract and pipeline
  tests, schema isolation tests, this document.
- Changed: fixture LLM answer now carries valid `[n]` markers; `RAGService` validates citations.
- Not done / blockers: everything in section 5. Nothing was pushed, committed, or merged.

## 7. Environment note

Gradle tests fail with `ClassNotFoundException` for every test class when the project path contains a non-ASCII
character (here the accented "e" in "técnicas") on this Windows setup. Run the build from a copy or checkout under an ASCII path.
