# VidaCruz — Executive Summary and Index (20-section deliverable)

Status: **draft for review.** This document answers the required 20-section format of `prompt-init.txt` §16. Each section points to the document that holds the detail. Nothing here adds a requirement; every statement comes from the linked documents.

## 1. Executive summary

VidaCruz is a two-service platform for 12 emergency rooms. `triage-service` registers patients, classifies them into five levels, monitors SLA per level and escalates overdue patients once. `clinical-assistant-service` answers protocol questions with citations and keeps an urgency count built from Kafka events. Both services communicate through Kafka topic `triaje.eventos`, and neither calls the other synchronously. The plan targets three days, with architecture decisions accepted before each change that needs them.

## 2. Understanding of the challenge and scope

See `docs/requirements.md` §1 (objectives) and §2 (scope). Out of scope: frontend, real clinical use, hospital integration, identity verification.

## 3. Functional requirements

`docs/requirements.md` §4: RF-01 to RF-06.

## 4. Non-functional requirements

`docs/requirements.md` §7: NFR-01 to NFR-20. Technical constraints in §8 (TC-01 to TC-12).

## 5. Business rules and classification

`docs/requirements.md` §5: RN-01 (levels and examples E1–E6), RN-02 (SLA), RN-03 (escalation idempotency), RN-04 (audit), RN-05 (anti-hallucination). Validations in §6.

## 6. Use cases and scenarios

Scenarios are the acceptance cases in `docs/requirements.md` §13 (TST-CLS, TST-STA, TST-ESC, TST-AUD, TST-RAG, TST-RES, TST-IDM, TST-PRJ). Actor flows are in `docs/services/triage-service.md` and `docs/services/clinical-assistant-service.md`.

## 7. API contracts

`docs/requirements.md` §9: API-01 to API-06, with request and response examples. Authentication is DEC-05 (API key by default, JWT as documented alternative).

## 8. Kafka event contracts

`docs/requirements.md` §10: envelope (DEC-03), EVT-01 to EVT-04, at-least-once delivery, idempotent consumers. Durable history is OD-3, decided.

## 9. Domain model and persistence

`docs/requirements.md` §11: entities, constraints and indexes. Database per service (DEC-10, ADR-14).

## 10. Logical architecture of the microservices

`docs/architecture/diagrams.md` §1 (components) and `docs/capabilities-map.md` (capabilities C-01 to C-11, owners, dependencies).

## 11. Integration and consistency architecture

`docs/architecture/diagrams.md` §2 (registration with outbox), §3 (escalation with concurrency guard), §5 (event flow). Decisions: ADR-02, ADR-03, ADR-08.

## 12. RAG assistant design

`docs/architecture/diagrams.md` §6 (query flow) and §7 (circuit breaker). Corpus and acceptance questions: `docs/requirements.md` §12. Decisions: ADR-04 (pgvector isolation), ADR-05 (LLM provider), ADR-09 (circuit breaker). Note: ADR-05 and ADR-09 are Proposed.

## 13. Security and privacy

`docs/security/threat-model.md`: STRIDE analysis, clinical data handling, four gaps to decide (GAP-01 to GAP-04). Authentication: `docs/adr/ADR-07-authentication-api-key.md`.

## 14. Resilience and fault tolerance

`docs/requirements.md` §7 (NFR-03, NFR-04), RN-05 edge cases (LLM unavailable returns 503), and `docs/architecture/diagrams.md` §7. Timeouts and retry values are in the service docs.

## 15. Observability and operations

Metrics minimum set: NFR-07. Labels must avoid patient IDs and free text: NFR-19. Health probes: NFR-05. Correlation IDs: NFR-06. Operational checks are part of the pipeline in `docs/operations/ci-cd-pipeline.md`.

## 16. Infrastructure and deployment

docker-compose (NFR-12), Kubernetes manifests (NFR-13), Azure target (NFR-14, ADR-11). Pipeline proposal: `docs/operations/ci-cd-pipeline.md`. Repository layout proposal: `docs/implementation-roadmap.md` and `docs/adr-roadmap.md`; the layout is not forced until the build structure is checked.

## 17. Ambiguities, decisions and risks

- Ambiguities: `docs/requirements.md` §14 (AMB-01 to AMB-16).
- Decisions: `docs/requirements.md` §14, decision register (DEC-xx), and `docs/adr/`.
- Risks: `docs/requirements.md` §17 (summary) and `docs/security/threat-model.md` §5 (gaps).

## 18. Testing and traceability

Test strategy: `docs/requirements.md` §13 and `docs/services/*.md`. Traceability: `docs/traceability-matrix.md` (D5). Test gates: `docs/operations/ci-cd-pipeline.md`.

## 19. Methodology: SDD, ODD, TDD and OpenSpec deliverables

- SDD: specifications are the source of truth. `docs/requirements.md` and `docs/capabilities-map.md`.
- ODD: changes are ordered in `docs/openspec-roadmap.md` (changes 1 to 12).
- TDD: priority areas listed in `prompt-init.txt` §12.1 (classification, SLA, transitions, escalation, idempotency, RAG absence of evidence).
- OpenSpec: `openspec/changes/` holds approved change specs. Only changes that have passed the approval gate may start implementation.

## 20. Implementation roadmap and acceptance checklist

- Three-day plan with P0, P1 and P2 tasks: `docs/implementation-roadmap.md`. Day 1 entry gates G-01, G-02 and G-03 are listed there.
- Acceptance checklist: `prompt-init.txt` §17 (quality control). Status of each item is tracked in `docs/traceability-matrix.md`.

## Open decisions that block implementation

`docs/adr-roadmap.md` §3.3 gives the acceptance order. All 14 ADR files are `Proposed` at the time of writing; the scope of ADR-12 is accepted by the user. Confirm the status in each ADR file before acting on this summary.
