# TST-RAG verification: real Gemini embeddings

[Run 46045071910](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46045071910) passed the opt-in TST-RAG check against the real `gemini-embedding-2` endpoint. This page records what was executed, the raw evidence and the exact steps to reproduce it. The raw output is committed as [`gemini-embedding-tst-rag-results.json`](gemini-embedding-tst-rag-results.json); it is a byte-identical copy of the artifact uploaded by that run.

## Result at a glance

| Check | Recorded result |
|---|---|
| Model / dimensions | `gemini-embedding-2`, 768, `output_dimensionality=768` |
| Retrieval | 6 probes × 5 runs, all 5/5 passing, `top-k=4` |
| Separation | Minimum positive top score 0.7473 vs negative query 0.5842 |
| Negative query | Fixed notice returned, no evidence, no LLM call |
| Citation gate | PROTO-001 and PROTO-002 passed with stubbed LLM |
| Latency | 198–353 ms for embed + search across 30 runs |
| Requests / characters | 37 embedding requests, 3,687 input characters counted |
| Cost | **Not measured** — no billing API was queried |

## Quick path to reproduce in Actions

1. Confirm the Actions Secret `GEMINI_API_KEY` contains the rotated key. Never paste the value in an issue, PR or chat.
2. In Actions, select **Gemini embedding verification** → **Run workflow**. The workflow file must be on the repository default branch (`main`) for dispatch to list it; until then use the local path below.
3. Download the `gemini-embedding-verification` artifact from the completed run and open `results.json`.
4. Compare against the table above. Scores and latency are allowed to drift; pass/fail and the positive-vs-negative separation are the gates.

## Local reproduction

Requires Java 21, Docker (Testcontainers pulls `pgvector/pgvector:pg17`) and a valid key exported from your own secret store:

```bash
cd clinical-assistant-service/clinical-assistant-service
GEMINI_API_KEY="<rotated key from your secret store>" \
  ./gradlew test --no-daemon --tests 'com.vidacruz.clinical.integration.GeminiEmbeddingVerificationTest'
```

The test writes `build/reports/gemini-embedding-verification/results.json`. Without `GEMINI_API_KEY` the test is skipped, so a green local suite alone is not evidence of real-provider retrieval.

## What the test actually does

1. Applies all Flyway migrations to a disposable pgvector container, including the V4 seed with fixed fragment ids and the V6 lexical vectors.
2. Re-embeds the three seed fragments with the real endpoint using `title: none | text: ...`, replacing the lexical vectors in that container only. No production database is touched.
3. Runs five retrievals for each of six clinical probes: PROTO-001, PROTO-002 and the four unambiguous PROTO-003 scenarios (4 %, 6.5 %, 9 %, and 4 % with altered mental state). The 5 % phrasing stays documented as ambiguous and is not probed.
4. Checks citation grounding for PROTO-001/PROTO-002 with a stub `LLMRouter`, then checks that an unrelated diabetes query produces no evidence.
5. Records counts and measured latency in JSON. No billing data, patient data or chat-provider call is involved.

## Acceptance checklist

- [ ] Every probe reports `passed: true` and `passedRuns: 5`
- [ ] `minPositiveTopScore` is greater than `negativeQuery.topScore`
- [ ] Negative query has `noEvidence: true`, `answerIsFixedNotice: true`, `llmCalled: false`
- [ ] Both citation gates report `passesCitationGate: true`
- [ ] Cost remains explicitly marked as not measured unless billing data is later queried
- [ ] The artifact contains no credential values

## Out of scope

The chat LLM is a stub here. This run does not verify the chat route, R-07 (chat endpoint/id, latency and cost), R-06, the runtime configuration spike, or production re-ingestion. ADR-05 remains `Proposed` and no acceptance acta is issued from this evidence alone.

## Next step

[PR #104](https://github.com/Crispelinho/vidacruz-triage-platform/pull/104) was merged into `develop` as `bc6b411`; this page is the committed evidence record. [ADR-05](../../adr/ADR-05-assistant-llm-provider.md) remains `Proposed` because its remaining gates are still open.
