# OpenRouter evaluation: free external-client provider, documented only

OpenRouter is recorded as a **candidate** chat provider, not as an adopted chain node. A direct HTTP probe confirmed that its free router answers from an external client at `cost=0`, follows the citation/no-evidence contract, and can be constrained against training and paid fallback. No adapter, configuration or test was changed: Gemini stays the verified primary chat node and ADR-05 remains `Proposed`. Raw non-sensitive values are in [`openrouter-provider-evaluation-results.json`](openrouter-provider-evaluation-results.json). Neither file contains a credential, prompt text, answer text or account identifier.

## Quick path

1. Keep `RAG_LLM_CHAIN=gemini` for the demo. Do not add an OpenRouter chain node yet.
2. If a free external node is evaluated later, use `openrouter/free` with `provider.data_collection=deny` and `provider.max_price={"prompt":0,"completion":0}`.
3. Do **not** send `provider.zdr=true` to free models: it removes every free endpoint.

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
- [x] No repository code, configuration or test changed
- [ ] Trial key rotated after being pasted in plaintext in chat
- [ ] Five-run chat gate executed if the provider is ever adopted
- [ ] Cost confirmed against billing, not only the API `usage` field

## Next step

Keep this as a recorded alternative next to the [R-07 chat evidence](gemini-chat-r07-verification.md). If the Developer/User later chooses OpenRouter, open a separate issue and ADR-05 amendment before adding an adapter. Reproduce only with a rotated key from a secret store, injected as an environment variable; never paste it into a chat, issue, PR, file or metric label.

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
