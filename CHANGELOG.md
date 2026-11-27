# Changelog

All notable changes to the Vida Cruz triage platform are documented in this file. The format follows Keep a Changelog and releases use semantic versioning.

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
