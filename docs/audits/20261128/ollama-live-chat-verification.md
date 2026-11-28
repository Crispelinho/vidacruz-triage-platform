# Ollama live chat probe (2026-11-28)

## Scope

Opt-in probe `OllamaLiveVerificationTest` against a real Ollama server, executed through the production Spring AI
wiring: `ollamaNode` is a `SpringAIChatAdapter` over an `OllamaChatModel`, with `spring.ai.ollama.base-url` and
`spring.ai.ollama.chat.options.model` supplied from the environment. Retrieval evidence is the fixed PROTO-001
fixture. Only counts, statuses, model/provider identifiers, timings and provenance are recorded: no prompts, answer
text, patient data, credential value or account identifier is persisted. The generated probe IDs are synthetic test
values.

## Direct endpoint check

`POST http://127.0.0.1:11434/api/chat` against the local `ollama/ollama:latest` container with `llama3.1:8b`:

| Field | Value |
|---|---|
| HTTP status | 200 |
| Wall time | 115.34 s |
| `load_duration` | 101,503,885,805 ns |
| `total_duration` | 103,900,707,465 ns |
| `eval_count` | 2 |
| `prompt_eval_count` | 17 |
| `done` / `done_reason` | `true` / `stop` |

The first two attempts were aborted by the client at 60 s and 240 s. Container logs identify this as a cold load
(`client connection closed before llama-server finished loading, aborting load`); a successful `llama-server` start
took 155-175 s in an earlier run. An intermediate `400` was caused by a UTF-8 BOM in the request file and was fixed.

## Five-run probe

`generatedAt=2026-11-28T17:27:14.436421Z`, one unmeasured warm-up call, then five measured calls. Raw evidence:
[`ollama-live-results.json`](ollama-live-results.json).

| Run | Status | Provider / model | Answer chars | Latency |
|---|---|---|---|---|
| 1 | ok | `ollama` / `llama3.1:8b` | 111 | 85,516 ms |
| 2 | ok | `ollama` / `llama3.1:8b` | 111 | 221,610 ms |
| 3 | ok | `ollama` / `llama3.1:8b` | 111 | 247,540 ms |
| 4 | failed (`LLMUnavailableException`) | — | — | 300,251 ms |
| 5 | ok | `ollama` / `llama3.1:8b` | 111 | 274,786 ms |

Result: **4/5**. `nodeClass=SpringAIChatAdapter`, `nodeModel=llama3.1:8b`.

## Interpretation

- **Confirmed:** the static Spring AI Ollama keys are effective at runtime. Four completions came back through the
  real `ollama` node with the configured model id and a non-blank answer. This is the first live-provider evidence
  for those keys; previous confirmation used an NDJSON stub.
- **Not confirmed:** the five-run gate is **not closed**. The production `rag.llm.timeout` is `15s`; with that default
  the probe returned 0/5, each call failing at about 15 s with `ollama/llama3.1:8b unavailable`. The measured probe
  used a test-only `rag.llm.timeout=300s`, and one of five calls still exceeded it. The local CPU-only container is
  not representative of the C17 production endpoint behind Cloudflare Tunnel, so this result does not justify
  changing the production timeout.
- **Not measured:** cost, and the OpenCode node. Per the owner's decision of 2026-11-28, no paid OpenCode inference
  call was made; the standing rule keeps chat gates on OpenRouter. This probe does not close R-07, the full runtime
  spike or ADR-05 acceptance.

No retries were run after the second 4/5 result: further relaxation of the timeout would hide the latency finding
rather than resolve it.
