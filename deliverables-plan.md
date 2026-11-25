# VidaCruz — Deliverables Plan

Status: **retrospective/status as of 2026-11-25**. D0 (`docs/analysis.md`) and D1 (`docs/requirements.md`) were drafted first; D2–D6 are now committed on `develop` through PRs #1–#6, #10–#14 and #16. PR #17 adds the consolidated ADR set and the Spec C-05 change under `openspec/changes/event-driven-integration/`. The source documents remain `enunciado-prueba-dev-webflux.pdf` and `prompt-init.txt` (same folder).

## 1. Blocking decisions (resolve before writing deliverables)

| # | Issue | Evidence | Recommendation | Trade-off |
|---|---|---|---|---|
| D-1 | **RESOLVED (user, 2026-11-24): Spring Boot 3.x.** Both skeletons still target Spring Boot `4.2.0` and must be changed. | `triage-service/.../build.gradle.kts:3`, `clinical-assistant-service/.../build.gradle.kts:3` | Use the latest 3.5.x patch (verify on spring.io). Update both `build.gradle.kts`. | Boot 3.5 is past open-source support (see D-6). |
| D-2 | **RESOLVED by D-1:** Spring AI `2.1.0-M1` requires Spring Boot 4, so it cannot be used on Boot 3. | `clinical-assistant-service/.../build.gradle.kts:21` | Use the Spring AI 1.1.x GA line (verify version and its pgvector starter artifact on spring.io). | Spring AI 1.x is the Boot 3 line; 2.x is a major upgrade. |
| D-5 | **Spring Cloud BOM mismatch.** Both skeletons import `spring-cloud-dependencies:2026.0.0`, which is the Boot 4 line and only a milestone (`2026.0.0-M1`). | both `build.gradle.kts` lines 21/47 (triage) and 22/49 (assistant) | Use the Spring Cloud `2025.0.x` train (Northfields), built for Boot 3.5. Confirm the latest patch on spring.io. | The `2025.0.x` train is the last one for Boot 3. |
| D-6 | **Boot 3.5 open-source support.** 3.5 open-source support ended 2026-06-30; Spring Boot 3.5.16 is the last OSS patch of the line and Spring Cloud 2025.0.3 is the paired train. | `requirements.md` AMB-12 and the official 2026-06-25 spring.io announcement | Accept 3.5.x for the 3-day dev/eval scope (the brief requires 3.x); ADR-10 records the EOL risk and the post-delivery migration path. Paid support is not assumed. | Staying on EOL 3.5 means no free security patches after delivery. Boot 4.x would be supported but breaks the brief. |
| D-3 | RESOLVED: Java 21 target (DEC-13, user-approved 2026-11-24). Originally Java 25 toolchain. | both `build.gradle.kts` line 12 | Confirm the runtime available for the Dockerfile and Azure image. Java 21 is the safer default for a 3-day test. | Java 25 needs matching local JDK and base images. |
| D-4 | Deadline. The brief is dated 2026-10-29 with a 3-day window; today is 2026-11-24. | `enunciado` header | Confirm with the user whether the 3 days start now. | Affects backlog scope (P2 may be dropped). |

Only D-1 blocks the requirements document. The others are recorded as ADR or risk items.

## 2. Deliverables (7 documents)

Deliverables 1–6 are the files required by `prompt-init.txt` §11. Deliverable 0 is the 20-section analysis from §8. All go under `docs/` in the repo, on a feature branch (never `main`).

| ID | File | Purpose | Source | Depends on |
|---|---|---|---|---|
| D0 | `docs/analysis.md` | 20-section analysis (§8 order), including the consistency checklist (§9) | prompt §4, §8, §9 | — |
| D1 | `docs/requirements.md` | Master spec: scope, RF/RN/NFR, validations, API contracts, event contracts, acceptance criteria, ambiguities | prompt §11.1, Fase 1 | D-1 resolved |
| D2 | `docs/capabilities-map.md` | Capabilities (`platform-foundation`, `triage-classification`, `patient-lifecycle-audit`, `triage-sla-escalation`, `event-driven-integration`, `clinical-assistant-rag`, `emergency-state-projection`, `operational-reporting`, `medical-escalation-alerts`, `security-observability-delivery`) with justification for grouping | prompt §11.2, Fase 2 | D1 |
| D3 | `docs/adr-roadmap.md` | List of decisions that need an ADR, with status and template | prompt §4.O, §11.5 | D1 |
| D4 | `docs/openspec-roadmap.md` | Ordered incremental changes, each with requirements, capabilities, dependencies, files, tests, done-criteria | prompt §11.3, Fase 3 | D2, D3 |
| D5 | `docs/traceability-matrix.md` | Chain: source § → requirement → capability → change → task → code → test → evidence | prompt §5, Fase 5 | D1, D4 |
| D6 | `docs/implementation-roadmap.md` | Three-day P0/P1/P2 backlog with IDs, components, requirement IDs, verifiable results | prompt §7, §11.6 | D4, D5 |

Current state: D0–D6 are committed, so this section records the original plan rather than pending work. The consolidation PR #17 adds ADR-01/03/10/14, the OPEN-09/10 record and Spec C-05 without closing OPEN-09/OPEN-10, OD-5 or the broker-outage posture. Executable Outbox/Kafka integration tests are defined in Spec C-05 §7 and mapped to T-02/T-04/T-16/T-27, but are not implemented in the scaffold branch.

Writing order (historical): **D0 → D1 → (D2, D3 in parallel) → D4 → D5 → D6**. D5 was started in parallel with D4 once D1 and D2 were stable.

## 3. Content plan per deliverable

### D0 `analysis.md` (20 sections, in order)
1 Executive summary · 2 Requirements inventory · 3 Requirements per microservice · 4 API contracts · 5 Business rules and validations · 6 Reactive architecture · 7 Data model · 8 Kafka contracts · 9 RAG design · 10 SLA and notifications · 11 Reports and SQL · 12 Security and resilience · 13 Observability · 14 Infrastructure and deployment · 15 Test strategy · 16 Recommended ADRs · 17 Traceability matrix (summary) · 18 Ambiguities and risks · 19 Three-day backlog · 20 Final compliance checklist.

Each requirement gets: ID, name, description, type (EXPLICIT / DERIVED / PROPOSED / AMBIGUOUS), origin section, component, dependencies, priority, acceptance criterion, test type.

### D1 `requirements.md`
- **Functional:** RF-01 to RF-06, with the endpoints named in the brief (`POST /triage/pacientes`, `PATCH /triage/pacientes/{id}/estado`, `GET /triage/pacientes/{id}`, `POST /asistente/consulta`, `GET /asistente/estado-urgencias`, `GET /triage/reportes/operacion`).
- **Business rules:** RN-01 decision table with the six example patients (E1–E6) as acceptance cases; RN-02 SLA table; RN-03 idempotency; RN-04 audit fields; RN-05 anti-hallucination matrix.
- **Validations:** the full RF-01 range table, including diastolic < systolic cross-field rule.
- **Non-functional:** reactive (no blocking on event loop), Kafka, Resilience4j, observability, security, containers, Azure.
- **Corpus:** PROTO-001, PROTO-002, PROTO-003 and the three RAG example queries.
- **Ambiguities:** see §4 below.

### D2 `capabilities-map.md`
One block per capability: ID, name, goal, owning service, requirement IDs, dependencies, specs needed, acceptance criteria, tests. Justify any merge or split against the 10 proposed capabilities.

### D3 `adr-roadmap.md`
One entry per decision, with: context, problem, alternatives, recommended option, justification, positive/negative consequences, risks, status. Minimum set from §4.O plus the brief: service decomposition, Kafka messaging and message format, transactional outbox, vector store, LLM provider and Spring AI vs LangChain4j, RF-06 notification channel, authentication (JWT vs API key), projection store for the emergency state, degraded mode (503 fallback), R2DBC vs JDBC for the report query.

### D4 `openspec-roadmap.md`
Ordered changes, for example: platform foundation (skeletons on Boot 3, Docker Compose, CI) → triage classification (RF-01, RN-01) → patient lifecycle and audit (RF-02, RN-04) → SLA and escalation (RF-03, RN-02, RN-03) → Kafka event contract and outbox → emergency-state projection (RF-04 Part B) → RAG assistant with circuit breaker (RF-04 Part A, RN-05) → operational report SQL (RF-05) → notifications (RF-06) → security and observability → Kubernetes and Azure. Each change lists dependencies and a done-criterion that is a test or evidence, not an opinion.

### D5 `traceability-matrix.md`
Table form: source § | requirement ID | capability | OpenSpec change | task ID | code path (filled later) | test ID | evidence. Columns for code and evidence stay empty until implementation. Include a check that every EXPLICIT requirement appears at least once.

### D6 `implementation-roadmap.md`
P0 = required by the brief (RF-01…RF-05, RN-01…RN-05, Kafka including the outbox writer/publisher, RAG with fallback, tests, docker-compose, README, the ADR set from `adr-roadmap.md`, manifests). P1 = robustness beyond the minimum (publisher backoff/backlog policy, observability hardening, security hardening). P2 = extras (OpenTelemetry, IaC). Each task: ID, description, component, requirement IDs, dependencies, verifiable result, tests.

## 4. Ambiguities and gaps found in the brief (feed D0, D1, D3)

These are documented decisions, not questions for the evaluator (the brief says there will be no answers).

1. **FC 40–89 is not covered by levels 1–3.** A patient with FC 60 and no other criteria falls to NO_URGENTE. Literal reading; clinically notable. Do not change the rule (prompt rule 9); record it as a risk.
2. **"Mayor gravedad" vs numeric level 5.** NO_URGENTE_2 has the highest number but is the least severe. Decision: evaluate levels 1→4 in order, first match wins; level 5 applies only when level 4 matches and the complaint is administrative.
3. **Administrative complaint matching.** Exact list is `documentos`, `recolección de resultados`, `renovación de recetario`, `vacunación`, `seguimiento`. Decision: case- and accent-insensitive, trimmed comparison. Document it.
4. **RF-05 for patients who never reach consultation** (direct ALTA, still waiting). Decision: exclude from average and p95 and count them in the total; document the choice.
5. **Undefined errors:** unknown patient ID (assume 404); same-state transition, e.g. EN_CONSULTA → EN_CONSULTA (assume 409).
6. **ESCALADO is a flag, not a state.** A escalated patient stays `REGISTRADO` with `escalado: true`.
7. **Empty sources.** The response shape for "no protocol available" is undefined. Decision: `respuesta` contains the fixed notice and `fuentes: []`, returned with 200 unless a different code is chosen and recorded in an ADR.
8. **Time zones.** `desde`/`hasta` are ISO 8601 with no zone. Decision: require offset or `Z`; store UTC.
9. **Authentication scope.** JWT or API key is left open. Decision in an ADR; define which endpoints require it (probably all `/triage/**` and `/asistente/**`, not health probes).
10. **RF-06 channel** is open by design. The ADR must compare at least email, webhook, and a queue-based alert, and pick one that works within three days.
11. **Azure deployment** requires a subscription and a registry. If unavailable, document the manifests and the blocker rather than claim deployment.

## 5. Working rules for this plan

- Language: this document is a technical artifact, so English. The chat summary is in Spanish.
- Each deliverable is written to `docs/` on a feature branch. Nothing is committed to `main`. Commits and pushes happen only on the user's approval.
- Source code, Gradle changes and OpenSpec change folders are out of scope for documentation PRs; they are tracked in `docs/implementation-roadmap.md` and Spec C-05 and require a separate approved implementation task.
- A requirement is marked done only with a linked test or evidence (prompt Fase 5).
