# OpenRouter evaluation: free external-client provider, documented only

OpenRouter is recorded as a **candidate** chat provider, not as an adopted chain node. A direct HTTP probe confirmed that its free router answers from an external client at `cost=0`, follows the citation/no-evidence contract, and can be constrained against training and paid fallback. An opt-in adapter was later added under issue #143, but it is not part of the default chain: Gemini stays the verified primary chat node and ADR-05 remains `Proposed`. Raw non-sensitive values are in [`openrouter-provider-evaluation-results.json`](openrouter-provider-evaluation-results.json). Neither file contains a credential, prompt text, answer text or account identifier.

## Quick path

1. Keep `RAG_LLM_CHAIN=gemini` for the demo. The OpenRouter node is registered but opt-in; enable it explicitly only after a rotated key and an accepted ADR-05 amendment.
2. The adapter pins a free model (`nvidia/nemotron-3.5-lightning:free`) and always sends `provider.data_collection=deny` and `provider.max_price={"prompt":0,"completion":0}`.
3. Do **not** send `provider.zdr=true` to free models: it removes every free endpoint. The adapter deliberately omits it.

## Implementation note (2026-11-27, issue #143)

- `OpenRouterHttpLLMAdapter` implements `NamedLLMProvider` with key `openrouter` and calls the OpenAI-compatible `/chat/completions` endpoint using the shared grounded prompt.
- `OpenRouterProperties` binds `openrouter.*`; the API key comes only from `OPENROUTER_API_KEY` and defaults to empty. No key value is stored in source, configuration, tests, docs, logs or metrics.
- `RAGConfiguration` registers the `openRouterNode` bean, but `rag.llm.chain` still defaults to `ollama,gemini,opencode`. Enable with `RAG_LLM_CHAIN=openrouter,gemini` only after the gate work below is done.
- `openrouter.data-collection` is validated to `deny`; the zero price ceiling is hardwired, so a paid fallback cannot be selected by configuration. `openrouter/model` is configurable for a later five-run gate.
- Tests: `OpenRouterHttpLLMAdapterWireTest` checks the endpoint, Bearer header, privacy body, response parsing and 429-to-unavailable mapping against a local stub; config tests cover defaults, overrides, explicit chain selection and invalid privacy values.
- This is implementation only. It does not close live-provider, cost or five-run chat verification, and it does not accept ADR-05.

## What was measured

| Check | Recorded result |
|---|---|
| Endpoint | `POST https://openrouter.ai/api/v1/chat/completions` (OpenAI-compatible) |
| Credential | Trial key injected only as an environment variable; no value stored in files, commits, docs or memory |
| `openrouter/free`, probe 1 | `200`; routed to `recursive/flint-v1.0:free`; 4,327 ms; 77 prompt / 107 completion tokens; `cost=0` |
| `openrouter/free`, probe with privacy controls | `200`; routed to `recursive/flint-v1.0:free`; 4,327 ms; 77 / 107 tokens; `cost=0` |
| `nvidia/nemotron-3.5-lightning:free` | `200`; 2,808 ms; 98 / 530 tokens; `cost=0` |
| `google/gemma-4-26b-a4b-it:free` | `429`, temporarily rate-limited upstream |
| `particle-ai/particle-1:free` | `200` in the first probe, `429` in a later probe |
| `provider.zdr=true` | `404 No endpoints found matching your data policy (Zero data retention)` |
| `data_collection=deny` + `max_price=0` | `200`, `cost=0`; free routing still available |
| Grounded answer, supported case | `[1]` citation marker present |
| Grounded answer, unsupported case | Exact `SIN_EVIDENCIA` notice with no citation |
| Model selection | Non-deterministic: `openrouter/free` chose different models across calls |

The probes used synthetic, de-identified questions only. No prompt or answer text was copied into either artifact. Token and `cost` values come from the API `usage` object; they are **not** billing evidence.

## Privacy handling

| Control | Effect |
|---|---|
| `provider.data_collection=deny` | Restricts routing to providers that may not store data; usable on the free tier |
| `provider.max_price={"prompt":0,"completion":0}` | Keeps the request free; a paid fallback would otherwise be billed |
| `provider.zdr=true` | Incompatible with free endpoints in this workspace; returns `404` |
| OD-16 | Still applies: no patient identifiers in the free-text question; no automatic scrubber |

The API returned an account identifier in an error payload during probing. It was not copied to any artifact and must not be recorded in logs or metrics.

## Why it is not adopted now

- The free tier is documented for experimentation and low volume; upstream `429`s were observed within a handful of calls. It cannot support the target availability.
- Free endpoints have no ZDR route, so strict zero-retention routing cannot be combined with `cost=0`.
- The free router changes the responding model between calls, which makes the five-run chat gate harder to reproduce. A pinned `:free` model plus an explicit free-only fallback would be needed.
- `cost=0` is API metadata, not an invoice. Measured cost is still an open gate for ADR-05.

## Checklist

- [x] Free external-client access verified with a live HTTP probe
- [x] Citation and no-evidence behaviour verified on synthetic, de-identified inputs
- [x] Privacy controls and their free-tier limits recorded
- [x] Opt-in adapter, configuration and stub tests added under issue #143
- [ ] Trial key rotated after being pasted in plaintext in chat
- [ ] Five-run chat gate executed if the provider is ever adopted
- [ ] Cost confirmed against billing, not only the API `usage` field

## Next step

Keep this as a recorded alternative next to the [R-07 chat evidence](gemini-chat-r07-verification.md). The adapter added under issue #143 is opt-in and does not adopt the provider; enabling it still requires the rotated key, measured cost, the five-run chat gate and a formal ADR-05 acceptance. Reproduce only with a rotated key from a secret store, injected as an environment variable; never paste it into a chat, issue, PR, file or metric label.

```powershell
$env:OPENROUTER_API_KEY='<from secret store>'
$body=@{
  model='openrouter/free'
  messages=@(@{role='user';content='<de-identified question>'})
  provider=@{ data_collection='deny'; max_price=@{prompt=0;completion=0} }
} | ConvertTo-Json -Depth 8 -Compress
Invoke-RestMethod -Method Post -Uri 'https://openrouter.ai/api/v1/chat/completions' `
  -Headers @{ Authorization="Bearer $env:OPENROUTER_API_KEY" } `
  -ContentType 'application/json' -Body $body
```
