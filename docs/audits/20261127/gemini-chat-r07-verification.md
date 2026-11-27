# R-07 verification: real Gemini chat endpoint and model id

This page records the runtime evidence for the ADR-05 R-07 gate. It covers the **chat** node only: the endpoint, the responding model id and the measured latency. The raw values are in [`gemini-chat-r07-results.json`](gemini-chat-r07-results.json). No credential, prompt or answer is stored in either file.

## Result at a glance

| Check | Recorded result |
|---|---|
| Endpoint | `POST /asistente/consulta` with `X-API-Key`, local Compose stack on port 18082 |
| Chain | `RAG_LLM_CHAIN=gemini` |
| Status | `200` |
| `modelo_usado` | `gemini/gemini-3.5-flash-lite` |
| Response keys | `respuesta`, `fuentes`, `citas`, `modelo_usado` |
| Citation gate | 1 source, 1 citation, `[n]` marker present |
| Wall-clock latency | 8,239 ms |
| Application LLM latency | 7.167916953 s, one `success` sample in `assistant_llm_latency_seconds` |
| Cost | **Not measured** — no billing API queried; Gemini free tier is documented at no per-token charge |
| Privacy | No prompt, answer, key or provider header in container logs |

## What was executed

1. Recreated only `clinical-assistant-service` with a temporary Compose override setting `RAG_LLM_CHAIN=gemini` and `RAG_LLM_TIMEOUT=600s`. The override lives outside the repository; the delivered Compose defaults are unchanged.
2. Injected a short-lived trial key solely as the `GEMINI_API_KEY` environment variable of the command. It was not written to a file, committed, logged or put in a metric label.
3. Waited for `/actuator/health` to return `200`, then sent one `POST /asistente/consulta` with the standard API key header and a synthetic clinical question containing no patient identifier.
4. Read `modelo_usado`, the response field names and source/citation counts. The answer text itself was not copied to the artifact.
5. Read `assistant_llm_latency_seconds_count/sum{outcome="success"}` from `/actuator/prometheus` with the API key header.
6. Made one independent `generateContent` request to capture `usageMetadata` from the same model. This probe is **not** the RAG request body and its token counts are not extrapolated to the app call.
7. Scanned the container logs for the prompt, key prefixes and the provider header; all checks returned false.

## What this closes

- **R-07 endpoint and id:** the configured `gemini-3.5-flash-lite` endpoint answered at runtime and identified itself as `gemini/gemini-3.5-flash-lite` in `modelo_usado`.
- **R-07 per-node latency:** one successful Gemini attempt measured by Micrometer at 7.167916953 s, with 8.239 ms wall-clock for the whole HTTP request.
- **Runtime spike (partial):** `RAG_LLM_CHAIN` and `GEMINI_API_KEY` were proven effective in the assembled stack. `ollama.*`, `opencode.*` and the static `spring.ai.ollama.*` keys were not part of this run.

## What remains open

- **Cost:** not measured. No billing API was queried and no invoice was inspected; the free-tier classification is a documented property, not billing evidence. OpenCode prepaid cost is still unmeasured.
- **TST-RAG / chat:** this is a single smoke call, not five runs per PROTO probe. PR #104's TST-RAG evidence uses a stub LLM and does not close this gate for chat.
- **Runtime spike (remainder):** the other two chain nodes and the Spring AI static keys are unverified. Ollama answered only the no-evidence notice (`modelo_usado=none`) in this environment; its first real attempt timed out at the 15 s chain limit because local CPU inference took about 206 s, and a 600 s override produced no evidence rather than a model answer. OpenCode returned `503 LLM_UNAVAILABLE` with a cause not yet isolated.
- **ADR-11 target evidence:** ADR-11 lists `docs/operations/despliegue-azure-y-pruebas.md` as the R-06/T-24 execution record, and ADR-05 now records that artifact as partial evidence. The environment was retired on 2026-11-27, so a reproducible rerun, budget-alert evidence, manifest validation and secret-hygiene confirmation are still open before acceptance.
- ADR-05 remains `Proposed`; this artifact does not issue an acceptance acta.

## Reproduction

Use your own rotated key from a secret store. Never paste it into a PR, issue, chat or file.

```powershell
$env:RAG_LLM_CHAIN='gemini'
$env:GEMINI_API_KEY='<from secret store>'
$o='<local override with RAG_LLM_CHAIN=gemini and RAG_LLM_TIMEOUT=600s>'
$c=@('-f','docker-compose.yml','-f','docker-compose.demo.yml','-f',$o,'-p','vidacruz-e2e','--profile','ollama')
docker compose @c up -d --force-recreate clinical-assistant-service
```
