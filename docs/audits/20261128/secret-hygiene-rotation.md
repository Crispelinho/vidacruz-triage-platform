# ADR-11 secret hygiene and rotation record (2026-11-28)

Scope of this record: **secret names and update metadata only**. No secret value, prompt, response text, patient data or account identifier is recorded here. This follows `docs/operations/adr11-secret-hygiene-evidence-template.md`.

## Owner attestation

On 2026-11-28 the owner confirmed that all keys and secrets were rotated, which lifted the previous ban on new live provider calls. The statement is an owner attestation; it does not by itself prove provider-side revocation of every previously exposed value.

## GitHub metadata (names and timestamps only)

`gh secret list --repo Crispelinho/vidacruz-triage-platform --env azure` reports these environment-scoped secrets, all updated on 2026-11-28:

| Secret name (no value) | Updated (UTC) |
| --- | --- |
| `ASSISTANT_API_KEY` | 11:30:05 |
| `TRIAGE_API_KEY` | 11:30:23 |
| `POSTGRES_PASSWORD` | 11:31:17 |
| `REDIS_PASSWORD` | 11:30:54 |
| `GEMINI_API_KEY` | 11:32:30 |
| `OPENROUTER_API_KEY` | 11:33:13 |
| `OPENCODE_API_KEY` | 11:37:17 |

The repository-level secret list shows the same seven names with earlier update timestamps (2026-11-27). Protected verification workflows read the environment-scoped values in `azure`; no value was read locally. The `gemini-verification` and `openrouter-verification` environments have no environment-scoped secrets of their own.

## Hygiene checks

- No secret value was printed, copied into a file, or used outside GitHub Actions during this review. Commands used `gh secret list`, which returns names and timestamps only.
- A prior credential scan in run `46091452672` reported zero matches and is recorded in [`openrouter-chat-verification.md`](openrouter-chat-verification.md). This record does not repeat that scan.
- Repo history and built images have **not** been fully scanned in this session. `trivy` is not installed on this workstation and `docker scan` is unavailable, so the image-scan item remains open.

## Outcome

**PARTIAL.** Rotation is recorded from owner attestation plus GitHub update metadata. Formal ADR-11 acceptance still requires a full repo/image/history hygiene check and image scan. No ADR status change is made in this record.
