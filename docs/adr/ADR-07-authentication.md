# ADR-07: Authentication: API key versus JWT

- **Status:** Superseded by [ADR-07-authentication-api-key.md](ADR-07-authentication-api-key.md), which was accepted and implemented. Historical comparison of API key versus JWT only.
- **Date:** 2026-11-25
- **Deciders:** developer (pending)
- **Scope:** Decides the authentication mechanism for protected Triage and Clinical Assistant endpoints: API key in `X-API-Key`, validated by a reactive filter on `/triage/**` and `/asistente/**`, with health/probe endpoints public and keys supplied by environment/secret configuration. It does not decide authorization rules, data protection policy, or functional request validation (C-10/C-02), and it does not decide provider-specific secret handling beyond coordination with ADR-05/ADR-09/ADR-11.
- **Related:** C-10, DEC-05, NFR-09, NFR-10, AMB-09, G-21, G-T31, T-19, T-28, [requirements.md](../requirements.md), [analysis.md](../analysis.md), [capabilities-map.md](../capabilities-map.md), [clinical-assistant-service.md](../services/clinical-assistant-service.md), [triage-service.md](../services/triage-service.md), [implementation-roadmap.md](../implementation-roadmap.md), [openspec-roadmap.md](../openspec-roadmap.md), [adr-roadmap.md](../adr-roadmap.md), [ADR-05-assistant-llm-provider.md](ADR-05-assistant-llm-provider.md), [ADR-09-circuit-breaker-degraded-mode.md](ADR-09-circuit-breaker-degraded-mode.md), [ADR-11-azure-target.md](ADR-11-azure-target.md)

## Context

NFR-10 requires authentication on endpoints, and NFR-09 requires no hardcoded credentials with secrets supplied from environment variables or secret stores. DEC-05 proposes an API key in the `X-API-Key` header, validated by a WebFlux filter, with JWT as the documented alternative. AMB-09 records the same tradeoff: API key is simpler to test; JWT with an external IdP is the heavier alternative. D2 C-10 fixes the boundary: ADR-07 decides the authentication mechanism and protected endpoint scope (`/triage/**` and `/asistente/**`), while authorization rules, data protection, and functional request validation remain in C-10/C-02. D3 also requires this ADR before change 3 and change 10.

The service analyses leave key lifecycle details open. G-21 and G-T31 identify unresolved key source/provisioning, rotation, per-service key strategy, filter position, public endpoint behavior, and 401 body shape. Clinical Assistant and Triage analyses both propose API key from environment variable, one key per service, constant-time comparison in a WebFlux filter, and 401 on protected endpoints with health probes public; these are implementation proposals, not accepted decisions. ADR-07 must coordinate secrets with ADR-11 and provider key handling with ADR-05/ADR-09 because those decisions also involve secret/configuration boundaries.

## Decision drivers

- NFR-10: protected endpoints must reject unauthenticated requests.
- NFR-09, DER-01: credentials/keys must come from environment/secret configuration; no hardcoded secrets.
- DEC-05, AMB-09: evaluation setup should be simple to test, while keeping JWT as an explicit alternative.
- C-10 / roadmap §3.1: the ADR must decide mechanism and scope only; it must not decide functional validation or authorization policy.
- G-21, G-T31: key rotation, per-service keys, and 401 body details must be made explicit before implementation acceptance.

## Alternatives considered

| Option | Description | Pros | Cons | Status / why not chosen |
|---|---|---|---|---|
| **A. API key in `X-API-Key`, validated by reactive filter** | Require `X-API-Key` on `/triage/**` and `/asistente/**`; validate with constant-time comparison in a WebFlux filter; keep `/actuator` health/probes public; load keys from environment/secret config. | Matches DEC-05; simple for local/docker-compose/evaluation; no external IdP dependency; easy to test per endpoint. | Key rotation/provisioning/per-service strategy and 401 body are still pending; weaker identity model than token-based auth; coarse-grained unless extended later. | **Proposed**, pending developer confirmation. |
| **B. JWT with external IdP** | Validate bearer JWTs issued by an external identity provider; enforce auth on protected endpoints through JWT validation middleware/filter. | Standard token model; supports expiry, issuer/audience validation, and future identity/authorization claims; better fit for multi-user production scenarios. | Adds IdP integration, token lifecycle, configuration, and test complexity; out of scope for the three-day evaluation unless explicitly chosen. | Rejected for now per DEC-05/AMB-09; remains the documented alternative. |
| **C. No authentication** | Leave endpoints public and rely on network/perimeter controls only. | Lowest implementation cost. | Violates NFR-10 and NFR-09 security intent; exposes clinical/operational endpoints; unacceptable for the stated requirements. | Rejected: incompatible with NFR-10. |

## Decision

**Proposed: Option A.** Both services authenticate protected endpoints with an API key supplied in `X-API-Key`, validated by a reactive WebFlux filter on `/triage/**` and `/asistente/**`. Health/readiness/liveness endpoints remain public. API keys are loaded from environment variables or secret configuration; no key is hardcoded in source, images, or manifests. The proposal assumes constant-time comparison and records one key per service as a pending implementation detail from G-21/G-T31, but the developer must confirm key source, rotation, per-service key policy, and 401 error body before acceptance. JWT with an external IdP remains the documented alternative and is not selected. This ADR does not decide authorization rules, data protection behavior, or request validation semantics; those remain in C-10 and C-02.

## Consequences

- Positive: satisfies NFR-10 with low implementation and test complexity for the evaluation timeline.
- Positive: keeps secret handling aligned with NFR-09 and deployable through environment/secret configuration.
- Negative: API keys provide coarse authentication and require careful rotation/secret management; they do not provide user identity claims.
- C-10 implements the filter and rejection behavior for every protected endpoint; C-02 continues to own functional validation and 400/409 request semantics.
- Change 3 and change 10 depend on this decision; ADR-11 and ADR-05/ADR-09 must coordinate secret naming/storage so keys are not managed inconsistently.

## Risks and mitigations

- **NFR-09 / G-T31 (secret leakage or poor key lifecycle):** load keys from env/secret config only; add secret scan T-28; document rotation/provisioning before acceptance.
- **NFR-10 (missing endpoint coverage):** verify 401 on every protected endpoint, including `/asistente/estado-urgencias`; keep probes public by explicit allowlist.
- **G-21 (401 contract ambiguity):** define 401 body shape in line with NFR-20 before implementation; do not invent an undocumented response format.
- **C-10/C-02 boundary drift:** keep validation errors in C-02 and authentication failures in C-10; tests must distinguish 400/401/409 cases.
- **Future scalability:** if multi-user identity or granular authorization becomes required, revisit JWT/IdP as a superseding ADR.

## Verification

- **T-19 / implementation-roadmap.md:79:** unauthenticated requests to every protected `/triage/**` and `/asistente/**` endpoint return 401; authenticated requests with the configured key succeed according to endpoint behavior.
- **T-28 / implementation-roadmap.md:94:** repository/image secret scan finds no hardcoded API keys or provider secrets.
- **openspec-roadmap.md:35 / openspec-roadmap.md:41:** change 3 and change 10 record authentication as required before protected endpoint delivery.
- **triage-service.md:443-445 and clinical-assistant-service.md:466:** endpoint security tables/tests confirm missing or wrong `X-API-Key` returns 401 while health/probe endpoints remain public.
