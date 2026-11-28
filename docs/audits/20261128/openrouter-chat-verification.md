# OpenRouter chat verification: all gates passed (2026-11-28)

## Goal

Verify the chat gates that were still unverified after the Gemini embedding run: five runs per positive probe, invalid-citation rejection with a real model, and negative/no-evidence probes. The user corrected the earlier direction: use **OpenRouter**, not OpenCode.

## What was built

- Issue [#148](https://github.com/Crispelinho/vidacruz-triage-platform/issues/148), branch `test/opencode-chat-gates` from `develop` (`e2ff83c`), PR [#150](https://github.com/Crispelinho/vidacruz-triage-platform/pull/150).
- `OpenRouterChatVerificationTest` uses the opt-in `OpenRouterHttpLLMAdapter` with `OPENROUTER_API_KEY` injected only from the Actions Secret. Retrieval is local and deterministic (lexical embeddings in disposable pgvector), so the chat model is the only external variable.
- Every request carries `provider.data_collection=deny` and a zero price ceiling. `provider.zdr` is deliberately absent because the evaluated free endpoints reject it.
- The `workflow_dispatch` workflow uploads an evidence artifact with counts, timings, protocol IDs and HTTP status only: no prompt text, answer text, patient data, key value or account identifier.

## Final live run

[Run 46091452672](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46091452672), `provider=openrouter`, `model=openrouter/free`, 16 real model calls.

| Gate | Result |
| --- | --- |
| Five-run positive chat gate | **Passed 15/15.** Three protocols (`PROTO-001`, `PROTO-002`, `PROTO-003`), 5 runs each; every run had one valid citation, the expected protocol and `openrouter/openrouter/free` provenance. App latencies 1,386–28,447 ms. |
| Invalid-citation rejection | **Passed.** A real model answer was mutated deterministically to cite `PROTO-999` before entering the domain pipeline; `InvalidCitationException` was raised, the answer was parsed but the invalid source was observed, and no answer text was retained in the exception. |
| Negative/no-evidence probes | **Passed 2/2.** Fixed notice returned with zero LLM calls. |
| Cost | Not measured. The adapter pins a zero price ceiling and no billing API was queried. |

Earlier attempts in the same branch are informative:

- [46089852521](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46089852521) attempt 1 died before compile with Maven Central HTTP `429` during Spring Boot plugin resolution; retry passed dependency resolution. Attempt 2 had 15/15 positive runs, but the invalid-citation probe never reached the model because its question did not clear the retrieval threshold.
- [46090776462](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46090776462) forced retrieval, so the model was called, but it followed the grounding instruction and returned only valid sources. The gate was therefore exercised by injecting a known-bad citation into the real answer instead of relying on the model to misbehave.

## Verification notes

- Local `compileTestJava` passed on every commit.
- Local clinical suite baseline: 252 tests, 1 failed, 2 skipped. The single failure was a Testcontainers wait strategy in `ClinicalSchemaIsolationTest`; its isolated rerun passed in 2m36s.
- The trial key pasted in chat remains compromised. Only the rotated key in the `OPENROUTER_API_KEY` Actions Secret was used; its value was never read into files, logs or this document.

## Scope limits

- No ADR-05 acceptance, no default-chain change, no cost claim and no deployment.
- Citation validation proves provenance against the corpus and retrieved set; it does not prove semantic entailment of the answer text.
- The workflow file was temporarily placed at the legacy `.github/workflows/opencode-status-probe.yml` path so an existing numeric workflow ID could dispatch the feature ref; the final commit restores the descriptive `openrouter-chat-verification.yml` name.
