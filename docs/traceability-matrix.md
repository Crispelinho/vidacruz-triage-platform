# VidaCruz — Traceability Matrix (D5)

Status: **draft for review.** Plan level: the chain is complete up to the OpenSpec change. Task, code, test and evidence columns are filled during implementation. Nothing here is marked done.
Sources: `docs/requirements.md` (D1, §4 RF, §5 RN, §6 VAL, §7 NFR, §8 TC, §9 API, §10 EVT, §14 AMB, §16 DER), `docs/capabilities-map.md` (D2), `docs/openspec-roadmap.md` (D4, change numbers 1…12), `docs/adr-roadmap.md` (D3).

Branch context: part of the D3–D6 planning set proposed in PR #3 (https://github.com/Crispelinho/vidacruz-triage-platform/pull/3). It is not integrated into `develop`.

## 1. Chain

Each row follows the chain required by `prompt-init.txt` §5:

source § → requirement ID → capability → OpenSpec change → task → code path → test → evidence

- **source §**: the section of `requirements.md` where the ID is defined.
- **capability**: C-xx from D2. **change**: the number in D4 §3.
- **task**: the task ID in the change's `tasks.md`. Pending until the change exists.
- **code path**, **test**, **evidence**: filled during implementation. A row is done only when all of them are filled and the test passed.

Legend for the status column: `plan` (the row is assigned, nothing built), `done` (test and evidence linked).

## 2. Functional requirements

| Source § | ID | Capability | Change | Task | Code path | Test | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
| §4 | RF-01 | C-02 | 3 `triage-classification` | pending | — | — | — | plan |
| §4 | RF-02 | C-03 | 4 `patient-lifecycle-audit` | pending | — | — | — | plan |
| §4 | RF-03 | C-04 | 5 `triage-sla-escalation` | pending | — | — | — | plan |
| §4 | RF-04-A | C-06 | 8 `clinical-assistant-rag` | pending | — | — | — | plan |
| §4 | RF-04-B | C-07 | 9 `emergency-state-projection` | pending | — | — | — | plan |
| §4 | RF-05 | C-08 | 6 `operational-reporting` | pending | — | — | — | plan |
| §4 | RF-06 | C-09 | 7 `medical-escalation-alerts` | pending | — | — | — | plan |

## 3. Business rules

| Source § | ID | Capability | Change | Task | Code path | Test | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
| §5 | RN-01 | C-02 | 3 `triage-classification` | pending | — | — | — | plan |
| §5 | RN-02-A | C-02 | 3 `triage-classification` | pending | — | — | — | plan |
| §5 | RN-02-B | C-04 | 5 `triage-sla-escalation` | pending | — | — | — | plan |
| §5 | RN-03 | C-04 | 5 `triage-sla-escalation` | pending | — | — | — | plan |
| §5 | RN-04-R | C-02 | 3 `triage-classification` | pending | — | — | — | plan |
| §5 | RN-04-T | C-03 | 4 `patient-lifecycle-audit` | pending | — | — | — | plan |
| §5 | RN-05 | C-06 | 8 `clinical-assistant-rag` | pending | — | — | — | plan |

## 4. Validations (RF-01)

| Source § | ID | Capability | Change | Task | Code path | Test | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
| §6 | VAL-01 | C-02 | 3 `triage-classification` | pending | — | one test per rule | — | plan |
| §6 | VAL-02 | C-02 | 3 `triage-classification` | pending | — | one test per rule | — | plan |
| §6 | VAL-03 | C-02 | 3 `triage-classification` | pending | — | one test per rule | — | plan |
| §6 | VAL-04 | C-02 | 3 `triage-classification` | pending | — | one test per rule | — | plan |
| §6 | VAL-05 | C-02 | 3 `triage-classification` | pending | — | one test per rule | — | plan |
| §6 | VAL-06 | C-02 | 3 `triage-classification` | pending | — | one test per rule | — | plan |
| §6 | VAL-07 | C-02 | 3 `triage-classification` | pending | — | one test per rule | — | plan |
| §6 | VAL-08 | C-02 | 3 `triage-classification` | pending | — | one test per rule | — | plan |
| §6 | VAL-09 | C-02 | 3 `triage-classification` | pending | — | one test per rule | — | plan |
| §6 | VAL-10 | C-02 | 3 `triage-classification` | pending | — | one test per rule | — | plan |
| §6 | VAL-11 | C-02 | 3 `triage-classification` | pending | — | one test per rule | — | plan |

Each VAL rule has its own row so that the test and evidence can be linked one by one during implementation.

## 5. Non-functional requirements

| Source § | ID | Capability | Change | Task | Code path | Test | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
| §7 | NFR-01 | C-01 (checked in 3, 4, 6, 8) | 1 `platform-foundation` | pending | — | — | — | plan |
| §7 | NFR-02 | C-05 | 2 `event-driven-integration` | pending | — | — | — | plan |
| §7 | NFR-03 | C-06 | 8 `clinical-assistant-rag` | pending | — | — | — | plan |
| §7 | NFR-04 | C-05 (architecture check), C-04 (functional test) | 2 `event-driven-integration` (architecture), 5 `triage-sla-escalation` (functional test, T-12) | pending | — | — | — | plan |
| §7 | NFR-05 | C-11 | 11 `observability` | pending | — | — | — | plan |
| §7 | NFR-06 | C-11 | 11 `observability` | pending | — | — | — | plan |
| §7 | NFR-07 | C-11 | 11 `observability` | pending | — | — | — | plan |
| §7 | NFR-08 | C-04 | 5 `triage-sla-escalation` | pending | — | — | — | plan |
| §7 | NFR-09 | C-10 | 10 `security` | pending | — | — | — | plan |
| §7 | NFR-10 | C-10 | 10 `security` | pending | — | — | — | plan |
| §7 | NFR-11 | C-02 | 3 `triage-classification` | pending | — | — | — | plan |
| §7 | NFR-12 | C-01 | 12 `platform-delivery` | pending | — | — | — | plan |
| §7 | NFR-13 | C-01 | 12 `platform-delivery` | pending | — | — | — | plan |
| §7 | NFR-14 | C-01 | 12 `platform-delivery` | pending | — | — | — | plan |
| §7 | NFR-15 | C-01 | 1 `platform-foundation` | pending | — | — | — | plan |
| §7 | NFR-16 | not scheduled | — (P2 in D6) | — | — | — | — | optional |
| §7 | NFR-17 | C-01 | 1 `platform-foundation` | pending | — | — | — | plan |
| §7 | NFR-18 | process rule | every change | — | — | — | git history | plan |
| §7 | NFR-19 | C-11 | 11 `observability` | pending | — | — | — | plan |
| §7 | NFR-20 | C-02 | 3 `triage-classification` | pending | — | — | — | plan |

Notes:
- NFR-16 (OpenTelemetry) is bonus. It is not in D4 and is listed as P2 in D6. It is not claimed as covered.
- NFR-18 is a working rule, not code. Its evidence is the commit history of each change.
- NFR-04 covers only the direction stated in the requirement: Triage must not depend on the Assistant. The reverse guarantee (the Assistant's projection makes no call to Triage, change 9, T-16) is a complementary architectural check. It is not direct coverage of NFR-04 and is not counted in its row.

## 6. Technical constraints

| Source § | ID | Capability | Change | Task | Code path | Test | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
| §8 | TC-01 | C-01 | 1 `platform-foundation` | pending | — | — | — | plan |
| §8 | TC-02 | C-01 (all services) | 1 `platform-foundation` | pending | — | — | — | plan |
| §8 | TC-03 | C-01 | 1 `platform-foundation` | pending | — | — | — | plan |
| §8 | TC-04 | C-05 | 2 `event-driven-integration` | pending | — | — | — | plan |
| §8 | TC-05 | C-06 | 8 `clinical-assistant-rag` | pending | — | — | — | plan |
| §8 | TC-06 | C-06 | 8 `clinical-assistant-rag` | pending | — | — | — | plan |
| §8 | TC-07 | C-06 | 8 `clinical-assistant-rag` | pending | — | — | — | plan |
| §8 | TC-08 | C-05 | 2 `event-driven-integration` | pending | — | — | — | plan |
| §8 | TC-09 | C-01 | 1 `platform-foundation` | pending | — | — | — | plan |
| §8 | TC-10 | C-01 | 1 `platform-foundation` | pending | — | — | — | plan |
| §8 | TC-11 | C-01, C-06 | 1 and 8 | pending | — | — | — | plan |
| §8 | TC-12 | C-01 | 1 `platform-foundation` | pending | — | — | — | plan |

## 7. API contracts

| Source § | ID | Capability | Change | Task | Code path | Test | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
| §9 | API-01 | C-02 | 3 `triage-classification` | pending | — | — | — | plan |
| §9 | API-02 | C-03 | 4 `patient-lifecycle-audit` | pending | — | — | — | plan |
| §9 | API-03 | C-03 | 4 `patient-lifecycle-audit` | pending | — | — | — | plan |
| §9 | API-04 | C-06 | 8 `clinical-assistant-rag` | pending | — | — | — | plan |
| §9 | API-05 | C-07 | 9 `emergency-state-projection` | pending | — | — | — | plan |
| §9 | API-06 | C-08 | 6 `operational-reporting` | pending | — | — | — | plan |

## 8. Event contracts

| Source § | ID | Capability | Change | Task | Code path | Test | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
| §10 | EVT-01 | C-05 (produced by C-02) | 2 produced in 3 | pending | — | — | — | plan |
| §10 | EVT-02 | C-05 (produced by C-04) | 2 produced in 5 | pending | — | — | — | plan |
| §10 | EVT-03 | C-05 (produced by C-03) | 2 produced in 4 | pending | — | — | — | plan |
| §10 | EVT-04 | C-05 (produced by C-03) | 2 produced in 4 | pending | — | — | — | plan |

Consumers: EVT-01…EVT-04 are consumed by change 9 (projection). EVT-02 is also consumed by change 7 (alerts). The consumer tests belong to those changes.

## 9. Derived requirements and ambiguities

| Source § | ID | Capability | Change | Task | Code path | Test | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
| §16 | DER-01 | C-01 | 1 `platform-foundation` | pending | — | — | — | plan |
| §16 | DER-02 | C-05 | 2 `event-driven-integration` | pending | — | — | — | plan |
| §16 | DER-03 | C-05 | 2 `event-driven-integration` (guard used in 7) | pending | — | — | — | plan |
| §16 | DER-04 | C-01 | 1 `platform-foundation` | pending | — | — | — | plan |
| §16 | DER-05 | C-02 | 3 `triage-classification` | pending | — | — | — | plan |
| §16 | DER-06 | C-06 | 8 `clinical-assistant-rag` | pending | — | — | — | plan |
| §16 | DER-07 | C-11 | 11 `observability` | pending | — | — | — | plan |
| §16 | DER-08 | C-02 | 3 `triage-classification` | pending | — | — | — | plan |
| §14 | AMB-04 | C-04 | 5 `triage-sla-escalation` | pending | — | — | — | plan |
| §14 | AMB-05 | C-08 | 6 `operational-reporting` | pending | — | — | — | plan |
| §14 | AMB-09 | C-10 | 10 `security` | pending | — | — | — | plan |
| §14 | AMB-10 | C-09 | 7 `medical-escalation-alerts` | pending | — | — | — | plan |
| §14 | AMB-12 | C-01 | 1 `platform-foundation` | pending | — | — | — | plan |
| §14 | AMB-13 | C-01 | 1 `platform-foundation` | pending | — | — | — | plan |

Other AMB rows (AMB-01 to AMB-03, AMB-06 to AMB-08, AMB-11, AMB-14 to AMB-16) are decisions inside a change's design. They are traced when the owning change writes its `design.md`, and they are not listed here as separate rows yet.

## 10. Completeness check

Rule: every EXPLICIT requirement in `requirements.md` appears at least once in this matrix (the rule in `deliverables-plan.md` §3, D5).

Result at this draft: a script compared every requirement ID defined in `requirements.md` (RF, RN, VAL, NFR, TC, API, EVT, DER; 67 IDs) with this document, and none is missing. Split units (RF-04-A/B, RN-02-A/B, RN-04-R/T) are traced by their unit ID. The check must be repeated before the matrix is approved, because the requirements may change.

Open items that block a complete check:
- **OD-2 — numbering.** `prompt-init.txt` §5 uses RF-01…RF-09. This matrix follows `requirements.md` (RF-01…RF-06). The prompt is not edited here.
- **Task IDs** are pending until each change has its `tasks.md`.
- **Code, test and evidence columns** are empty until implementation. No row is marked done.

## 11. Completion criteria for this deliverable

1. Every EXPLICIT requirement appears in at least one row.
2. Every row names a capability (C-xx) and an OpenSpec change number from D4.
3. Every change in D4 appears in at least one row.
4. No row is marked `done` in this draft.
