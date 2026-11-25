# ADR-13: SLA evaluation mechanism (scheduled job, conditional update)

- **Status:** Proposed. Not accepted. The mechanism and write rule are proposed, but the default interval and the final rule for later SLA configuration changes remain pending developer confirmation.
- **Date:** 2026-11-25
- **Deciders:** developer (pending)
- **Scope:** C-04 `triage-sla-escalation` in Triage: how overdue `REGISTRADO` patients are detected and escalated once. It chooses the evaluation mechanism and the write rule for the escalation effect. It does not decide the alert channel (ADR-06), the domain state model (DEC-02), the event envelope (ADR-02), the outbox mechanism (ADR-03), or the unresolved interval and SLA-change values (G-T27, G-T28).
- **Related:** C-04, C-05, DEC-04, DEC-02, RF-03, RN-02-B, RN-03, NFR-08, NFR-01, NFR-04, DER-01, DER-03, AMB-04, AMB-07, G-T27, G-T28, R-07, EVT-02, T-11, T-12, TST-ESC, ADR-02 and ADR-03 (proposed in PR #17), ADR-06, ADR-08, [adr-roadmap.md](../adr-roadmap.md), [requirements.md](../requirements.md), [analysis.md](../analysis.md), [capabilities-map.md](../capabilities-map.md), [triage-service.md](../services/triage-service.md), [implementation-roadmap.md](../implementation-roadmap.md), [openspec-roadmap.md](../openspec-roadmap.md), [ADR-08](ADR-08-urgency-projection-store.md)

## Context

RF-03 requires SLA monitoring and automatic escalation. DEC-04 proposes a scheduled evaluator with a configurable interval; RN-03 and [triage-service.md](../services/triage-service.md):60-63 require the conditional update `WHERE id = ? AND escalado = false AND estado = 'REGISTRADO'` and make audit plus EVT-02 the effect of only the evaluator that changes the row. AMB-04 and DEC-02 define overdue as elapsed time strictly greater than the stored SLA while `estado` remains `REGISTRADO`; patients in `EN_CONSULTA` or `ALTA` are never escalated (requirements.md:58-63, requirements.md:132-136, requirements.md:415). C-04 owns this rule; C-05 owns event transport.

NFR-08 and DER-01 require configurable values and startup validation. G-T27 proposes a 30 s default evaluator interval, but that value is pending confirmation under G-T27 and is not accepted here. G-T28 proposes that the `Clasificacion.sla_minutos` snapshot from registration remains authoritative and that later configuration changes apply only to new registrations; the final rule is pending confirmation (triage-service.md:519-520).

analysis.md:173-178 requires one winner per patient, no partial escalation, and a retry on the next interval after a failed run. triage-service.md:334 states that the evaluator runs on every replica and that correctness comes from the conditional update, not from leader election; capabilities-map.md:93-100 records the same one-winner semantics without using the term. analysis.md:12 flags blocking pieces in a reactive stack, and analysis.md:110 names scheduler work that touches the database; the evaluator must delegate R2DBC work off the event loop for NFR-01. implementation-roadmap.md:22 and implementation-roadmap.md:31 make gate G-06 an acceptance prerequisite before T-11.

Dependencies: ADR-02 and ADR-03 must be accepted before this mechanism can emit EVT-02 through the outbox. ADR-06 consumes EVT-02 for alerts, but a notification retry must never create an escalation (adr-roadmap.md:57, openspec-roadmap.md:32). ADR-08 ignores EVT-02 for urgency counts because the count is derived from registration, consultation and discharge events only.

## Decision drivers

- Escalate only when elapsed time is strictly greater than the stored SLA (AMB-04, RN-02-B).
- Exactly one escalation per patient under concurrent evaluators and multiple Triage replicas, without leader election (RN-03, DEC-04).
- Atomic flag change, audit row and EVT-02 outbox row; no partial escalation (DER-03, R-07, T-12).
- Configurable interval and SLA values with startup validation (NFR-08, DER-01); pending values are not fixed by this ADR.
- Non-blocking scheduler work that delegates database access to R2DBC (NFR-01, analysis.md:110).
- No synchronous dependency on the Assistant (NFR-04).
- The mechanism is testable in seconds with a short configured SLA (RF-03 acceptance, TST-ESC).

## Alternatives considered

| Option | Description | Pros | Cons | Status / why not chosen |
|---|---|---|---|---|
| **A. Scheduled evaluator in Triage with a conditional update** | A periodic job selects `REGISTRADO` patients with `escalado = false`; each candidate is escalated with the conditional update, and only the row-changing update writes audit and EVT-02 in one transaction | Matches DEC-04 and C-04; one winner per patient without leader election; retry is the next interval; short-SLA tests are straightforward | Adds a recurring database scan; interval choice affects worst-case delay; concurrency safety depends on the conditional update being enforced in every evaluator instance | **Proposed**, not rejected. Acceptance depends on G-06 and on confirmation of G-T27 and G-T28 |
| **B. Kafka-delayed messages** | Schedule a delayed message per patient that triggers escalation when the SLA expires | Aligns with the existing Kafka integration; avoids a polling scan | Needs a delay mechanism and cancellation when the patient leaves `REGISTRADO`; duplicates and replay can create late effects; changes the delivery contract beyond the scope of DEC-04 | Rejected: it adds a second scheduling path and makes state-dependent cancellation harder than the conditional update |
| **C. Quartz scheduler** | Introduce Quartz to run the evaluator with persisted job metadata | Mature scheduling and clustering features | Adds a new dependency and tables; its clustering model conflicts with the no-leader-election design in triage-service.md:334; unnecessary for one internal job | Rejected: the operational cost is not justified for this scope |
| **D. Standalone `Flux.interval` pipeline** | Drive the evaluation directly from a reactive interval without a separate scheduled-job boundary | Smallest code surface in a WebFlux service | The tick source alone does not provide winner selection or retry semantics; coupling ticks directly to R2DBC work increases the reactive-scheduler risk named in analysis.md:110 | Rejected as the standalone mechanism; a `Flux.interval` tick may be used inside Option A only after acceptance |

## Decision

**Proposed: Option A.** Run a scheduled evaluator inside Triage. Each run selects candidates with `estado = 'REGISTRADO'`, `escalado = false` and elapsed time strictly greater than the stored SLA, then executes `UPDATE ... WHERE id = ? AND escalado = false AND estado = 'REGISTRADO'`. Only when that update changes exactly one row does the evaluator write the `ESCALAMIENTO` audit row with equal states and the EVT-02 outbox row in the same transaction through C-05 and ADR-03. A failed run leaves no partial escalation and is retried on the next interval. No leader election is introduced; multiple replicas may run the evaluator and the database condition decides the winner. The default interval is pending under G-T27: 30 s is a proposal only. The treatment of later configuration changes is pending under G-T28: using the registration snapshot is a proposal only. This is a proposal, not an accepted decision.

## Consequences

- Positive: RN-03 escalation idempotency is enforced at the row level, so concurrent evaluators produce one effect per patient.
- Negative: detection latency depends on the accepted interval; a shorter interval increases database load. No numeric interval is accepted by this ADR.
- C-04 depends on C-02 and C-03 for state and history, and on C-05 for EVT-02 delivery; ADR-02 and ADR-03 are prerequisites.
- ADR-06 consumes EVT-02 for alerts, but retries must never create a new escalation (adr-roadmap.md:57, openspec-roadmap.md:32).
- ADR-08 ignores EVT-02 for urgency counts; it does not change the patient state represented in the projection.
- Triage keeps working when the Assistant is stopped, satisfying NFR-04; no synchronous call to the Assistant is added.
- Gate G-06 blocks T-11 until the developer accepts this ADR (implementation-roadmap.md:22, implementation-roadmap.md:31).

## Risks and mitigations

- **G-T27 (default interval):** keep the interval configurable under NFR-08; treat 30 s as a pending proposal and require developer confirmation before setting a default.
- **G-T28 (SLA snapshot versus later config changes):** keep the stored SLA as the proposed source for the patient deadline; record the final rule before acceptance so existing patients do not move when configuration changes.
- **No leader election (triage-service.md:334):** rely on the conditional update in every instance; T-11 runs two evaluators against the same database.
- **Reactive scheduler risk (analysis.md:12, analysis.md:110, NFR-01):** delegate R2DBC work off the event loop and verify with BlockHound or an equivalent check.
- **R-07 (dual write):** audit and EVT-02 are written in the same transaction as the flag change through the ADR-03 outbox, not by a separate publish step.
- **Failed runs:** rollback per patient and retry on the next interval; no partial escalation survives a failure (analysis.md:178).

## Verification

- **T-11:** with Testcontainers PostgreSQL and two evaluator instances, seed an overdue `REGISTRADO` patient and assert exactly one `escalado = true` update, one audit row and one EVT-02; run a second evaluation and assert no new row or event; add `EN_CONSULTA` and `ALTA` patients and assert no escalation; assert no escalation when elapsed equals SLA (implementation-roadmap.md:64, AMB-04).
- **TST-ESC:** repeat the overdue-once, no-op, state-exclusion and concurrent-evaluator cases as acceptance cases (requirements.md:390, triage-service.md:409-415). Use a short configured SLA so the test runs in seconds; the numeric value is a test fixture, not a production default.
- **T-12:** show that the audit row and EVT-02 commit together or neither commits, then stop the Assistant and show that registration, transitions and escalation all still succeed (implementation-roadmap.md:65, NFR-04).
- **OpenSpec change 5:** the done criterion is met only when the escalation is once only, the second evaluation is a no-op, two concurrent evaluators produce one escalation, and the final NFR-04 integration test passes (openspec-roadmap.md:30).
- **NFR-01 evidence:** BlockHound or an equivalent check shows no blocking call on the event loop while the scheduled evaluator performs its R2DBC work.
