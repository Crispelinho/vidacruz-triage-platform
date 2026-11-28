# OpenRouter chat verification: harness ready, live run pending (2026-11-28)

## Goal

Verify the chat gates that were still unverified after the Gemini embedding run: five runs per positive probe, invalid-citation rejection with a real model, and negative/no-evidence probes. The user corrected the earlier direction: use OpenRouter, not OpenCode.

## What was built

- Issue [#148](https://github.com/Crispelinho/vidacruz-triage-platform/issues/148), branch `test/opencode-chat-gates` from `develop` (`e2ff83c`).
- `OpenRouterChatVerificationTest` uses the opt-in `OpenRouterHttpLLMAdapter` with `OPENROUTER_API_KEY` injected only from the Actions Secret. Retrieval is local and deterministic (lexical embeddings in disposable pgvector), so the chat model is the only external variable.
- Every request keeps `provider.data_collection=deny` and a zero price ceiling. `provider.zdr` is deliberately absent because the evaluated free endpoints reject it.
- `workflow_dispatch` workflow uploads an evidence artifact with counts, timings, protocol IDs and HTTP status only: no prompt text, answer text, patient data, key value or account identifier.

## Gates

| Gate | Status |
| --- | --- |
| Five-run positive chat gate | Pending live dispatch. |
| Invalid-citation rejection with a real model | Pending a live answer. |
| Negative/no-evidence probes | Passed in every earlier harness run with zero LLM calls. |
| Cost | Not measured; the adapter pins a zero price ceiling and no billing API is queried. |

The trial key pasted in chat remains compromised. Only a rotated key stored as the `OPENROUTER_API_KEY` Actions Secret may be used; its value is never read into files, logs or this document.

## Out of scope

- No ADR-05 acceptance, no default-chain change and no deployment.
- This harness does not prove semantic entailment beyond citation provenance.
