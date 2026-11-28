# OpenCode chat verification: harness ready, provider blocked (2026-11-28)

## Goal

Verify the chat gates that were still unverified after the Gemini embedding run: five runs per positive probe, invalid-citation rejection with a real model, and negative/no-evidence probes. The user asked to use OpenCode instead of the OpenRouter trial provider.

## What was built

- Issue [#148](https://github.com/Crispelinho/vidacruz-triage-platform/issues/148), branch `test/opencode-chat-gates` from `develop` (`e2ff83c`).
- `OpenCodeChatVerificationTest` uses the existing `OpenCodeHttpLLMAdapter` with `OPENCODE_API_KEY` injected only from the Actions Secret. Retrieval is local and deterministic (lexical embeddings in disposable pgvector), so the chat model is the only external variable.
- `workflow_dispatch` workflow with evidence upload. The artifact records counts, timings, protocol IDs and HTTP status only: no prompt text, answer text, patient data, key value or account identifier.
- Two live dispatches were executed: [run 46087048561](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46087048561) and [run 46088869665](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46088869665).

## Results

| Gate | Result |
| --- | --- |
| Five-run positive chat gate | **Not verified.** 15 calls, 0 answered; every call returned HTTP `402`. |
| Invalid-citation rejection with a real model | **Not exercised.** The model never produced an answer. |
| Negative/no-evidence probes | **Passed 2/2.** Both returned the fixed notice with zero LLM calls. |
| Cost | Not measured. OpenCode is prepaid and no billing API was queried. |

The local clinical suite ran 252 tests with one failure and two skips. The failure was `ClinicalSchemaIsolationTest` timing out in a Testcontainers wait strategy, unrelated to the chat harness; its isolated rerun passed in 2m36s.

Provider diagnosis: the Actions Secret exists and was accepted by the workflow, but `opencode/glm-5.3` returns `402` for insufficient balance. A previously recorded free-model probe ([run 46063009871](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46063009871)) returned `429` with `FreeUsageLimitError` for the free models available under the same secret, and one model was unavailable. No free OpenCode model was usable at verification time.

## Gates still open

- Fund the OpenCode account or supply a key with available quota, then rerun the workflow.
- With a working model, close the five-run gate and the invalid-citation rejection gate.
- Keep ADR-05 `Proposed`; these results do not adopt a provider.
- No deployment, cost claim or chain change is implied by this harness.

## Security

No credential value was pasted into a file, commit, doc, log or metric. The previous OpenRouter trial key pasted in chat remains compromised and must be rotated. The existing `OPENROUTER_API_KEY` and `OPENCODE_API_KEY` values remain in GitHub Actions Secrets only, and their values were never read.
