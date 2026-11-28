# Changelog

All notable changes to the Vida Cruz triage platform are documented in this file. The format follows Keep a Changelog and releases use semantic versioning.

## [1.0.5] - 2026-11-28

### Added

- Strict ArchUnit allow-list for the triage domain: `domain..` and `event.domain..` may depend only on the JDK, Reactor and themselves. Persistence, SQL reports, transactions, response encoding and outbox writes now sit behind domain ports with infrastructure adapters (#166, issue #156).
- Gateway routes for `/ops/{triage,asistente}/health` and `/ops/{triage,asistente}/prometheus` (the latter still requires `X-API-Key`), plus A-5 CI hardening with branch guards, SHA-pinned actions, protected environments and no persisted checkout credentials (#160).

### Fixed

- Swagger UI, WebJars and `/v3/api-docs` are public in both API key filters while business endpoints stay protected (#165, issue #164).

### Notes

- This release makes no Azure deployment or acceptance claim; ADR-05 and ADR-11 remain `Proposed`.
- OpenRouter chat-gate evidence is recorded, but rotation of the compromised trial key is still pending; no new live provider calls are made here.

## [1.0.4] - 2026-11-28

### Fixed

- Azure PostgreSQL `azure.extensions` Bicep configuration now sets `source: user-override`, so a fresh `provision.ps1` succeeds without a manual parameter change.

## [1.0.3] - 2026-11-28

### Fixed

- Azure PostgreSQL now allow-lists the `vector` extension through Bicep, so the clinical assistant Flyway migration runs on a fresh provision without a manual server parameter.
- The strict G-04 grounded-answer decoder accepts LLM responses wrapped in a markdown JSON code fence while keeping validation of `respuesta` and `fuentes` unchanged.

### Added

- Enunciado compliance matrix documenting coverage of the delivered requirements.

## [1.0.2] - 2026-11-27

### Fixed

- Azure deployment now injects `SPRING_DATASOURCE_PASSWORD`, `SPRING_R2DBC_PASSWORD`, `SPRING_R2DBC_URL` and `SPRING_R2DBC_USERNAME` in the base manifests, matching the local Kubernetes overlay so both services start without manual patching.

## [1.0.1] - 2026-11-27

### Fixed

- Azure deployment builds each service from its module directory so Gradle finds `gradlew` during the image build.

## [1.0.0] - 2026-11-27

First evaluation release of the two-service platform: triage service and clinical assistant service.

### Added

- RF-01/RF-02/RF-03 patient registration, classification and idempotency, with transactional outbox publishing.
- RF-04/RF-05 grounded clinical answers with citations and the operation report; RF-04B urgency projection into Redis.
- RF-06 persisted SLA escalation alerts delivered through a live SSE stream.
- `X-API-Key` authentication and fail-closed validation on both services.
- End-to-end OpenTelemetry propagation across triage, Kafka and the clinical assistant, with a local Jaeger stack and NFR-07 metrics dashboards.
- Local Kubernetes overlay and a reproducible E2E harness with 33/33 passing checks.
- Azure Bicep infrastructure (AKS, PostgreSQL Flexible Server, container registry, identities) and Kustomize manifests with Gateway/HTTPS routes and cert-manager.
- CI validation of the delivered Kubernetes manifests with `kustomize build` and `kubeconform -strict`.
- Opt-in Gemini embeddings with the definitive 768-dimension pgvector schema and TST-RAG verification (5/5 per probe).
- Azure for Students budget of USD 100/year with alerts at 50 % and 80 % of actual spend.
- ADR-10 accepted for the documented evaluation scope; ADR-05 and ADR-11 remain `Proposed` with their verification gates recorded.

### Fixed

- Local Kubernetes tracing and startup against a restricted-cluster database.
- BlockHound reactive compliance and non-blocking outbox publishing.
- `pg_notify` to `LISTEN` alert delivery verified end to end.
- Deployment sizing: reduced pod CPU requests and a single `Standard_B4as_v2` node aligned with the 6-vCPU regional quota.
- README, architecture and ADR evidence alignment across the delivered branches.

### Notes

- ADR-11 still needs a reproducible Azure rerun and secret-hygiene evidence. This tag does not claim formal Azure acceptance.
- Spring Boot 3.5.16 is accepted only for the evaluation scope; production use requires a support contract or migration to Boot 4.1+.
