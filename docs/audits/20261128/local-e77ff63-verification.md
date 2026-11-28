# Local Kubernetes verification of `e77ff63`

## Scope

`e77ff63` is the `develop` head after PR #182 (Spring AI chat migration) merged at
`2026-11-28T13:36:46Z`. The local Docker Desktop Kubernetes cluster was verified from the
worktree at `C:\Users\HP\AppData\Local\Temp\opencode\vidacruz-final-e77ff63`, whose `HEAD` is
`e77ff63`, using `127.0.0.1` and the existing `verify-local-k8s-e2e.ps1` harness. This is a
**local** result: Azure runs `206ede7` and has not deployed `e77ff63`.

## Result

**33 PASS / 0 FAIL** (`local-e77ff63-k8s-e2e.txt`), covering health, Jaeger UI, all six triage
levels, state transitions, traceparent ingestion, SLA escalation, the full RF-06 acknowledgement
and SSE flow, reports, degraded assistant mode, API-key failures, both metrics endpoints,
outbox→Kafka trace propagation and the shared Jaeger trace.

## Earlier run and harness correction

The first run from the same commit was 30/33: only `RF-06 SSE snapshot/heartbeat`, `RF-06 SSE live
change` and `RF-06 alert resolved` failed. The harness started `curl -N` with `--max-time 22` and
then force-killed the process after 15 seconds, so the buffered tail of the stream was never
written to the capture file. A dedicated graceful-exit probe at `09:14Z` already showed
`heartbeat=True`, `alert_in_stream=True` and `resolved_in_stream=True` for the same local build.

`scripts/verify-local-k8s-e2e.ps1` now uses `--max-time 45` and waits for `curl` to exit naturally
before reading the file (with a forced kill only as a 50-second safety fallback). The rerun at
`09:35Z` then captured 39,559 bytes with the snapshot, heartbeat, live acknowledgement and
resolved event all present. No product code was changed for this result.

## Limits

- Host Ollama still does not answer a trivial prompt, so the real-provider Q1/Q2 chat checks were
  verified with the Gemini gate in Azure (`206ede7`), not with local Ollama. The local degraded
  path correctly returns `503 LLM_UNAVAILABLE` for Q3.
- The Compose wrapper `e77ff63-e2e-compose.ps1` was prepared but not launched.
- This local E2E does not authorize promoting `e77ff63` to `main`; `main` and tag `v1.0.6` are
  unchanged, and issue #181 remains open pending the Spring AI verdict.
