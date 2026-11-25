# C-05 Developer Decision Packet — D1–D7 and Implementation Authorization

- **Status:** **FACILITATION ONLY — OPEN. NOT APPROVED.** This packet contains no selection, no acceptance, no rejection, no gate closure, no test authorization and no repository action.
- **Date:** 2026-11-25
- **Role:** decision facilitator for the Developer/User. The facilitator may frame questions and evidence; it has no authority to decide.
- **Deliverable scope:** one decision packet for global nodes `D1`–`D7`, separate open matters, and one implementation-authorization checklist.
- **Authorization boundary:** no repository modification, no branch change, no commit, push, PR or merge; no test, BlockHound, `sdd-design` or `sdd-apply` run; no ADR-02/ADR-03/ADR-08/G-T25 acceptance or amendment; no real master architecture register written; no existing report changed.

## Sources read, read-only

| Key | Source | Status as found |
| --- | --- | --- |
| V3.1 | `C:\Users\HP\reports\C05-v2-blocker-resolution-plan-v3.1.md` | V3.1 proposal pending Developer/integrator review; not approved. §1 invariants, §4 DAG, §7 options, §9 P0 criteria, §11 boundary used. |
| REVIEW | `C:\Users\HP\reports\C05-v2-adversarial-review.md` | 24 findings `F-01`–`F-24`; both V2 proposals `REQUIRES REVISION`; demonstrated technical viability `BLOCKED`. |
| ART-SPEC | `C:\Users\HP\reports\C05-evidence-artifact-specs-P0.md` | 16 artifacts `ART-01`–`ART-16`, all literal `NOT EXECUTED`; zero P0 findings closed. |
| XW-REG | `C:\Users\HP\reports\C05-pr19-audit-and-contradiction-register.md` | `XW-01`–`XW-13` all OPEN; four-item PR #19 checklist PARTIAL / NOT SUPPORTED / PARTIAL. |
| MASTER-DRAFT | `C:\Users\HP\reports\MASTER-ARCHITECTURE-STATUS-draft-proposal.md` | Eight columns and all seed rows `PROVISIONAL — require developer approval`; real master file not written. |
| ADR-02 | Worktree `vidacruz-pr17`, branch `fix/pr19-conflicts`, `docs/adr/ADR-02-assistant-kafka-consumer.md` | `Proposed. Not accepted.` Option B proposed; numeric Redis dedupe TTL and gap limits unresolved. Branch was not changed. |
| ADR-03 | Same worktree/branch, `docs/adr/ADR-03-transactional-outbox.md` | Whole ADR `Proposed. Not accepted.`; broker-outage posture and G-T25 accepted as sub-decisions 2026-11-25; verification gates remain. |
| ADR-08 | Same worktree/branch, `docs/adr/ADR-08-urgency-projection-store.md` | `Proposed. Not accepted.` Option A proposed; backend/durability/TTL values open. |

---

## Executive summary — one page

Seven global decisions `D1`–`D7` remain **OPEN — unrecorded**. They are questions for the Developer/User, not authorizations already granted. `D1` asks whether the hardened `get()/join()/await()` equivalence rule is accepted under ADR-02 wording. `D2` — **globally reserved for the ADR-08 projection/idempotence backend** — asks which store backs the projection; the spring-kafka 4.2+ upgrade is **not** `D2` but the unselected local option `F04-B`. `D3` fixes durability `D`, persistence, fsync/replication, TTL versus retention and the C4 predicate. `D4` selects the gap policy and pending-recovery behaviour. `D5` decides whether `GAP_UNRESOLVED` and DLT records are terminal or replayable. `D6` fixes version posture and shutdown/cancellation semantics. `D7` fixes liveness thresholds and the acceptable spike result.

Every local option `F01-A` through `F06-C` in V3.1 §7 remains **UNSELECTED**. Three carry an explicit extra authorization note: `F03-C` (formerly `C3`), `F04-B` (formerly option `D2`, the upgrade), and `F06-C` (formerly `G3`, an NFR-01 exception). None is a gate closure; `F06-C` is not an ADR amendment.

The evidence position is unchanged: 24 findings remain open (6 P0, 14 P1, 4 P2); all 16 evidence artifacts `ART-01`–`ART-16` are `NOT EXECUTED`; all six P0 criteria are structurally verifiable but have pending definitions, pending evidence, and **none is satisfied**. Both V2 proposals remain `REQUIRES REVISION`; demonstrated technical viability remains `BLOCKED`. V3.1 itself remains pending and is not approved.

Four matters are deliberately kept separate from the seven-node packet: pause versus buffering, the recorded status of ADR-02/ADR-03/ADR-08/G-T25, gate acceptance, and the deferred LLM failover decision. No option is selected, no ADR is accepted or rejected, and no gate is closed by this document.

Implementation authorization is **not** in force. Before authorizing tests, BlockHound, `sdd-design`, `sdd-apply`, repository changes or merges, the Developer/User must record `D1`–`D7`, close the P0 criteria, execute and pass the named artifacts, resolve the cross-workstream register, explicitly settle ADR/gate status, and grant separate authorization for each execution or repository activity. Approval of V3.1 or of a planning document is not approval of any option, gate, test or merge.

---

## 1. Decision packet for global nodes `D1`–`D7`

### 1.1 Literal status rule

Every row below carries the literal state **`OPEN — unrecorded`**. This is a status, not an authorization. Investigation and drafting may proceed under declared open variables (V3.1 §4.3 and §5); guarantee statements, approvals and closures may not.

### 1.2 Local option catalog — all unselected

V3.1 §7 local IDs are scoped to their finding. They are **not** global decision nodes `D1`–`D7`.

| Local ID | Meaning as written in V3.1 §7 | Relevant node(s) | Authorization note |
| --- | --- | --- | --- |
| `F01-A` | Editorial separation only | `D1` | Unselected. No runtime cost; reviewer ambiguity remains until `D1`. |
| `F01-B` | Developer accepts hardened equivalence rule | `D1` | Unselected; requires explicit `D1` approval. |
| `F01-C` | Freeze baseline to literal `.block()` | `D1` | Unselected; equivalent waits stay outside the rule. |
| `F02-A` | Full transition table plus per-option observables | `D3`, `D4` evidence design | Unselected; falsifiable but depends on `F-03b`/`F-07`. |
| `F02-B` | Scope chain to current proposed route only | `D3`, `D4` evidence design | Unselected; risks implied endorsement (`F-12`). |
| `F02-C` | Declare chain open and remove completion claims | `D3`, `D4` evidence design | Unselected; documents stay unreviewable. |
| `F03-A` | Remove safety assertions; register `D` and rebuild open | `D3` | Unselected. |
| `F03-B` | Two replay flows: redelivery with dedupe on; rebuild with dedupe off after clearing markers and resetting offsets | `D3`, conditionally `D5` | Unselected; depends on `D2`/`D3`. |
| `F03-C` | Make markers effectively permanent within retention (formerly `C3`) | `D3` | **UNSELECTED — requires separate authorization; not a gate closure.** Higher storage cost; still needs `EventoHistorico` fallback. |
| `F04-A` | Application-authored ownership/generation guard at 3.3.16 | `D6` | Unselected; custom concurrency risk. |
| `F04-B` | Upgrade to spring-kafka 4.2+ and use `awaitAsyncResultsOnStop` (formerly option `D2`) | `D6` | **UNSELECTED — requires separate authorization; not a gate closure.** Requires Boot/dependency change and full regression under `D6`. **Not global `D2`.** |
| `F04-C` | Downgrade guarantee to documented risk; state no option satisfies it at 3.3.16 | `D6` | Unselected; lifecycle gate cannot pass. |
| `F05-A` | Separate pause-on-DLT from sequence-gap handling; require seek/resume or external replay | `D4` | Unselected; adds a mechanism to verify. |
| `F05-B` | Remove `1 → 3 → 2` from pause column; register as open `D4` consequence | `D4` | Unselected; must not imply buffering selection. |
| `F05-C` | Recover missing event through DLT path under pause | `D4`, conditionally `D5` | Unselected; depends on `D5` and `F-07`. |
| `F06-A` | Two frontiers: C4 effect frontier unrelaxed; separate durable replay/pending frontier | `D4`, with `D3` predicate | Unselected; requires durable pending schema. |
| `F06-B` | Single C4 frontier for both policies; no commit past pending | `D4`, with `D3` predicate | Unselected; removes buffering progress advantage. |
| `F06-C` | Amend ADR-02 with explicit NFR-01 exception treating pending durability as sufficient (formerly `G3`) | `D4` | **UNSELECTED — requires separate authorization; not a gate closure, not an ADR amendment.** |

### 1.3 Packets `D1`–`D7`

| Node | Exact question to answer | Options / tokens available in the cited sources | Technical consequences left open | Evidence artifacts required | Literal status |
| --- | --- | --- | --- | --- | --- |
| `D1` | Does the Developer accept the hardened equivalence rule treating `get()`, `join()` and `await()` as equivalent to ADR-02's literal `.block()` prohibition? | `F01-A`, `F01-B`, `F01-C`. `F-01a` (verbatim quote and "review baseline, not accepted decision" label) is independent and never accepts `D1`; only `F-01b` waits on `D1` (V3.1 edge 1). | Without a recorded answer, the normative baseline remains ambiguous; a reviewer can treat an unaccepted draft constraint as current ADR-02 wording and judge Option A non-compliant on an unsupported interpretation. `F-01` cannot close until no downstream claim depends on an undecided `D1`. | `ART-01`: verbatim ADR-02 status/no-block extract plus textual scan of both V2 documents; label pass must not be cited as equivalence acceptance. | **`OPEN — unrecorded`** |
| `D2` | Which projection/idempotence backend does ADR-08 select for the Assistant urgency projection? | ADR-08 alternatives: **Option A Redis with atomic Lua** (`Proposed`, not accepted), **Option B PostgreSQL** and **Option C in-memory map**, both marked Rejected in the ADR text. No global selection is recorded. **Global `D2` = ADR-08 backend only.** The spring-kafka upgrade is `F04-B`, not `D2` (V3.1 §7 note; XW-06). | Store-specific Lua/`ProcessedEvent` guidance is unsafe until the backend is named; `D3` recording waits by approval edge; `F-17` → `F-03b` waits strict. A PostgreSQL choice would invalidate Redis-specific durability and atomicity assumptions. `D2` approval is not ADR-08 acceptance unless separately approved. | `ART-03` (approved backend decision record), `ART-04`, `ART-06`, `ART-07`, `ART-10` (backend-specific configuration and tests). | **`OPEN — unrecorded`** |
| `D3` | What exactly counts as durability `D`: numeric persistence mode, fsync/replication acknowledgement, marker TTL versus Kafka retention, rebuild boundary and C4 predicate? | `F03-A`, `F03-B`, `F03-C` (extra authorization note). `F02-A/B/C` shape the required transition table but do not themselves fix `D`. `F06-A/B` depend on the C4 predicate. | Unconditional "replay is safe" and "no projection corruption" claims are unsupported; after a crash the effect can be suppressed by a surviving marker, double-applied if marker and `lastApplied` are lost, or skipped if the committed offset is past the effect. Rebuild equality counts, numeric TTL and retention threshold remain undefined; `D5` recording waits on `D3` by approval edge. | `ART-04`, `ART-05`, `ART-06`, `ART-07`, `ART-10`; three crash windows and both rebuild paths must end at the recorded baseline under a defined numeric predicate. | **`OPEN — unrecorded`** |
| `D4` | What gap policy governs `1 → 3 → 2`: partition pause, buffered commit frontier, pause scope, pending recovery and the admissible recovery mechanism? | `F05-A`, `F05-B`, `F05-C`; `F06-A`, `F06-B`, `F06-C` (extra authorization note). `F06-C` is not an ADR amendment. No pause-versus-buffering choice is selected. | The pause row is not executable while a truly paused partition cannot deliver later offset 2; buffering can lose pending state or relax C4 if pending durability is treated as effect durability. `F-05b`, `F-06c`, `F-09b`, `F-11b` and `F-21` are strict-gated; `D5` recording waits by approval edge. | `ART-05`, `ART-08`, `ART-09`, `ART-10`, `ART-12`, `ART-13`; single-partition `1,3,2` test and per-option pause/liveness spike remain unexecuted. | **`OPEN — unrecorded`** |
| `D5` | Are `GAP_UNRESOLVED` records and DLT records terminal, or replayable into the projection, and with what guard order and collision rule? | No exclusive `F0x` option is defined for `D5` in V3.1 §7. Conditional inputs only: `F03-B` (rebuild flow), `F05-C` (DLT replay under pause), `F06-A/B` (pending frontier). None selected. | Terminal/replayable status drives collision outcomes, DLT dedupe and guard order; an unresolved record can violate the terminal `ALTA` rule, double-apply DLT replay, or diverge from metrics depending on check order. Investigation may proceed; recording waits on `D3`/`D4` approval edges, while `F-09b` and `F-10` wait strict. | `ART-07` (conditional), `ART-08`, `ART-09` (conditional), `ART-10`, `ART-11`, `ART-12`. | **`OPEN — unrecorded`** |
| `D6` | What runtime/version posture governs shutdown and cancellation at the resolved spring-kafka version, including the presence or absence of `awaitAsyncResultsOnStop`? | `F04-A`, `F04-B`, `F04-C`. `F04-B` is the 4.2+ upgrade requiring separate authorization; it is **not** global `D2`. | At the pinned 3.3.16 posture, the container does not wait for in-flight async results; absolute "never re-commit after revocation" has no mechanism. Hook name, DLT-future propagation and version-scoped commit assertions remain undefined; `F-04` and `F-16c` wait strict. `F-18` version assertions wait on `F-20b`. | `ART-02` (executed resolved dependency report), `ART-05`, `ART-08`, `ART-12`, `ART-13`; stop/revoke/cancel tests with thread dumps and offset inspection are unexecuted. | **`OPEN — unrecorded`** |
| `D7` | What numeric liveness thresholds and acceptable spike result apply, and which per-option convergence assertion is valid? | No `F0x` option is defined for `D7`; `F-16a`–`F-16d` are subcomponents, not options. Thresholds must be recorded by the Developer/User; a test result may not invent them. | A uniform liveness test can pass vacuously or generalise incorrectly across blocking, reactor and async listener models. `F-16c` and `F-21` wait strict; **J5b is not closable by `D7` alone** — it also needs `D6`, `F-04`, `F-08`, `F-09`, `F-22`, `F-23` and `F-15b`. | `ART-09` (conditional), `ART-12` (definition record), `ART-13` (per-option timed tests); all unexecuted. | **`OPEN — unrecorded`** |

### 1.4 Dependency boundary reminder

- Edge kinds are `strict`, `partial`, `approval` and `evidence` (V3.1 §4). An edge expresses necessity only; it never authorizes work.
- `D2 → D3` and `D3/D4 → D5` are **approval** edges: investigation and design may proceed; only recording the decision waits.
- Parallel drafting is explicitly available for editorial and design work, but no guarantee is closed by drafting.
- `D1` is independent of the `F-01a` label correction. `F04-B`, `F03-C` and `F06-C` require separate authorization and are not gate closures.

---

## 2. Separate open matters — recorded, not decided

### 2.1 Pause versus buffering

| Item | Recorded state | Why it is separate | Literal status |
| --- | --- | --- | --- |
| Gap policy selection | No selection. `D4` is open; `F05-A/B/C` and `F06-A/B/C` remain unselected. | ADR-02 §Partition recovery records per-partition pause as **not verified** in the spike; buffering conflicts with Spec C-05 C4 unless a frontier exception is explicitly authorized (`F06-C`). The `1 → 3 → 2` row has no recorded seek/resume or replay mechanism. Evidence `ART-09`/`ART-13` is `NOT EXECUTED`. | **`OPEN — unrecorded`** |
| Cross-workstream causal chain | XW-07 and XW-13 remain open. The asserted chain "pause → ADR-03 lease TTL → gap policy" is **NOT SUPPORTED**; ADR-03/G-T25 binds the lease to the publisher publication budget, not consumer pause. | Collapsing publisher lease into consumer selection would scope ADR-03 against the wrong variable. | **`OPEN`** |

This packet does not recommend pause, buffering, seek/resume, DLT replay or an NFR-01 exception.

### 2.2 ADR-02 / ADR-03 / ADR-08 / G-T25 status

| Artifact | Status as recorded in the cited sources | Boundary observed here | Literal status |
| --- | --- | --- | --- |
| ADR-02 | `Proposed. Not accepted.` Option B is the proposed route; explicit exclusion of `@KafkaListener` as automatic fallback; literal rule is `.block()`; TTL and gap numeric limits unresolved (ADR-02 lines 3, 23, 33, 111–118). XW-01 records a competing V1 "ACCEPT" narrative. | No acceptance, rejection or amendment. `F-12` records existing status only. | **`OPEN`** |
| ADR-03 | Whole ADR `Proposed. Not accepted.` Accepted sub-decisions 2026-11-25: broker outage posture (backoff only, no breaker, no second broker) and G-T25 publisher client. Verification gates remain unchecked. XW-02 records whole-ADR versus sub-decision confusion. | No whole-ADR acceptance. No claim that accepted sub-decisions close the ADR or its gates. | **`OPEN`** |
| ADR-08 | `Proposed. Not accepted.` Option A proposed; backend, durability and TTL values open; G-11/G-24/OD-5 open. OPEN-09/OPEN-10 accepts `EventoHistorico` as durable source but does **not** accept ADR-08 (XW-12). | No acceptance/rejection. Global `D2` remains open and is not the upgrade option. | **`OPEN`** |
| G-T25 | Recorded as accepted 2026-11-25 for the **publisher only**: `KafkaTemplate` + Project Reactor, `Mono.defer(() -> Mono.fromFuture(...))`, `suppressCancel = false`; lease is a publisher-budget invariant. | No re-acceptance and no reuse as consumer proof. E7 ordering guarantee is not attributed to G-T25 (`F-13`, `F-14`). `ART-16` is `NOT EXECUTED`. | **ACCEPTED IN RECORD — publisher scope only; not reopened here** |

### 2.3 Gate acceptance

| Item | Recorded state | Consequence | Literal status |
| --- | --- | --- | --- |
| Roadmap gates `G-01`–`G-12` and C-05 completion criteria | No gate accepted or closed in this packet. SPEC-C05 §8 requires ADR-02/ADR-03/ADR-08 `Accepted` before implementation; IMPL-ROAD requires each ADR `Accepted` before its change starts and states that an unmet gate blocks the whole change. XW-10 records that merging PR #19 was **not** acceptance of its V2 proposal or closure of its audit. XW-05 records the unresolved tension between work under declared variables and "no workaround" gates. | Gate acceptance is a Developer/User act. A document merge, a recommendation, a draft status or an accepted sub-decision does not close a gate. | **`OPEN`** |
| P0 findings `F-01`–`F-06` | All six criteria structurally verifiable; definitions pending; evidence pending execution; **none satisfied**. | No P0 finding is closed by this packet. | **`OPEN`** |
| Master architecture status | MASTER-DRAFT has eight `PROVISIONAL` columns and illustrative seed rows; the real master file was not found or written. | Column schema and any real row update require explicit Developer approval. | **`OPEN`** |

### 2.4 Deferred LLM failover decision

| Item | Recorded state | Boundary observed here | Literal status |
| --- | --- | --- | --- |
| LLM failover / resilience decision | Carried under the Developer-only item `developer-decisions` as the **deferred LLM failover decision**. ADR-03 records that NFR-03 requires a breaker only for LLM calls (ADR-09); ADR-05 records the accepted sub-decision that LLM availability is not a readiness gate, endpoints return `503 LLM_UNAVAILABLE` and recover automatically when the provider returns; ADR-09 numeric values and 429/5xx classification remain open under G-05/CA-G-05. No second LLM provider, failover target, retry policy or numeric threshold is selected in the C-05 sources read for this packet. | Not one of `D1`–`D7`. Do not confuse with Kafka broker failover (ADR-03 explicitly rejects a second broker/cluster) or Redis transient failover handling (ADR-02 retry policy). No failover option is selected, recommended or authorized here. | **`OPEN — deferred to Developer/User`** |

---

## 3. Implementation-authorization checklist

**Nothing in this section is authorized by this packet.** Each row is a prerequisite to be satisfied or explicitly dispositioned before a separate authorization request is made.

### 3.1 Activity-level gate

| Activity | Minimum prerequisites before authorization | Current state |
| --- | --- | --- |
| Execute any test or fault injection | Rows C1–C10 below closed; relevant ART artifact spec reviewed; separate execution authorization per lane; `D` values and version posture recorded. | **`NOT AUTHORIZED`** |
| Run BlockHound | Rows C1–C8, C11 and C12 below closed; `ART-14` pin/module/launcher/install order/JVM flag/marker/negative control satisfied; `ART-15` tag filter proven; detection separated from liveness (`F-15b`, `F-16a`); separate control-run authorization. | **`NOT AUTHORIZED`** |
| Run `sdd-design` / `sdd-apply` | ADR-02/ADR-03/ADR-08 status explicitly decided; gates accepted; no unmet-gate workaround; `D1`–`D7` recorded; relevant spec text corrected; separate command authorization. | **`NOT AUTHORIZED`** |
| Change repository files, specifications or ADRs | Named file/section and scope approved by Developer/User; branch and change ownership recorded; no implicit acceptance from merge. | **`NOT AUTHORIZED`** |
| Commit, push, open PR or merge | Separate explicit authorization for each action; PR merge explicitly not treated as decision/gate/audit acceptance (XW-10). | **`NOT AUTHORIZED`** |

### 3.2 Numbered prerequisites

| # | Prerequisite to close or explicitly disposition | Source | Current literal state | Who may close/authorize |
| --- | --- | --- | --- | --- |
| C1 | Record a Developer dictamen on V3.1 and the independent reviews; note that approval of a planning document does not approve any option, gate, test or repo change. | V3.1 §11; work-state `v3.1` | V3.1 **`NOT APPROVED`** | Developer/User |
| C2 | Record all seven nodes `D1`–`D7` with the exact alternative and numeric values; none may remain `OPEN — unrecorded`. | V3.1 §1 invariant 4, §4, §7; MASTER-DRAFT §4 | All **`OPEN — unrecorded`** | Developer/User only |
| C3 | Close the six P0 criteria `F-01`–`F-06`; no downstream guarantee may depend on an undecided `D1`, `D2`, `D3`, `D4`, `D6` or `D7`. | V3.1 §9; ART-SPEC §3 | **None satisfied** | Developer/User after evidence |
| C4 | Execute and pass all 16 artifacts `ART-01`–`ART-16`, including backend-specific configuration, crash/rebuild, fault injection, stop/revoke, `1,3,2`, DLT, collision and publisher packs. | ART-SPEC §1 | All **`NOT EXECUTED`** | Developer/User authorization, then evidence lane |
| C5 | Fix all pending definitions: `D`, backend, numeric TTL, retention threshold, baseline-equality predicate, ownership hook name, recovery mechanism, acceptable duplicate count, pending store, terminal/replayable status, thresholds and version source. | V3.1 §9; ART-SPEC §1 predicates | **Definition pending** | Developer/User for decisions; evidence for values |
| C6 | Explicitly select or reject any local option with an authorization note — `F03-C`, `F04-B`, `F06-C` — and any A/B/C/D option. Do not infer selection from drafting or recommendation. | V3.1 §7 and §11 | All **UNSELECTED** | Developer/User |
| C7 | Resolve the `D2` token collision: global `D2` = ADR-08 backend; legacy addendum `D2` = upgrade → `F04-B`; `G3` → `F06-C`. Crosswalk is a reading aid, not a selection. | XW-REG XW-06; MASTER-DRAFT row "Legacy option-ID collision" | **`OPEN`** | Developer/User for terminology; read-only crosswalk |
| C8 | Fix ADR-02/ADR-03/ADR-08 status: accept, reject or explicitly leave Proposed; confirm G-T25 publisher-only scope; do not treat accepted sub-decisions as whole-ADR acceptance. | ADR-02/03/08; XW-01, XW-02, XW-12; ART-SPEC §2 | **`OPEN`** | Developer/User only |
| C9 | Accept or explicitly disposition gates `G-01`–`G-12` and C-05 completion criteria; no unmet gate may be worked around. | XW-05, XW-10; SPEC-C05 §8; IMPL-ROAD | **`OPEN`** | Developer/User |
| C10 | Resolve or disposition `XW-01`–`XW-13`, especially XW-03 and XW-04 source-not-locatable claims, XW-07 false causal chain, XW-08 citation error, XW-09 stale ADR paths and XW-13 dual dependency chains. | XW-REG §2–§3 | All **`OPEN`** | Developer/User / named owners |
| C11 | If PR #19 audit documents are used as evidence, complete F-01…F-24 traceability: currently 7/24 file-level, 0/24 content-verified, 0/24 section-level in merged docs. | XW-REG §1 and §1.1 | **PARTIAL** | Evidence owner after authorization |
| C12 | Approve the master status schema before creating or updating the real master register; no provisional column or seed row may be treated as accepted state. | MASTER-DRAFT §1–§5 | **`PROVISIONAL`**; real file not written | Developer/User |
| C13 | Record `F-15b` detection split, `ART-14` instrumentation marker and negative control throwing `BlockingOperationError`, and `ART-15` non-zero tag-filtered JUnit XML before any BlockHound pass is cited. | REVIEW F-15/F-22/F-23; ART-SPEC ART-14/15 | All **`NOT EXECUTED`** | Developer authorization, then evidence lane |
| C14 | For J5b, satisfy D7 thresholds **and** D6 version, `F-04`/`F-08`/`F-09` lifecycle, `F-22`/`F-23` harness proof and `F-15b` split. D7 alone is insufficient. | V3.1 §6 J5b; ART-SPEC ART-12 | **`OPEN`** | Developer/User + evidence lane |
| C15 | Resolve version posture via an executed dependency report (`ART-02`), including resolved spring-kafka/kafka-clients/Reactor/Testcontainers versions and shutdown-method presence, before version-specific claims. | REVIEW F-20; ART-SPEC ART-02 | **`NOT EXECUTED`** | Developer authorization, then evidence lane |
| C16 | Keep `ART-16` publisher evidence labelled publisher-only; do not reuse it for consumer ordering, ADR-02 selection or G-T25 whole-ADR closure. | REVIEW F-13/F-14; ART-SPEC ART-16 | **`NOT EXECUTED`** | Evidence lane; Developer authorization |
| C17 | Record or explicitly defer the LLM failover/resilience decision outside `D1`–`D7`; no failover target, retry policy or numeric breaker value may be inferred from C-05 planning. | Work-state `developer-decisions`; ADR-03 §Broker Outage; ADR-05/ADR-09 references | **`OPEN — deferred to Developer/User`** | Developer/User |
| C18 | Grant a separate, named authorization for every execution or repository action: tests, BlockHound, `sdd-design`, `sdd-apply`, installation, file edit, commit, push, PR and merge. | V3.1 §11; ART-SPEC §1; XW-REG §4 | **None granted by this packet** | Developer/User |

### 3.3 Stop condition

If C1–C18 are not closed or explicitly dispositioned, the correct state remains: planning and drafting may continue under declared open variables; no guarantee is asserted; no test, BlockHound, `sdd-*`, repository change or merge is authorized.

---

## 4. Boundary restatement

This packet was prepared by reading only. It did not select `F01-A`…`F06-C`, an A/B/C/D option, `F04-B`, `F03-C`, `F06-C`, pause or buffering; did not accept, reject or amend ADR-02, ADR-03, ADR-08 or G-T25; did not close `D1`–`D7`, `F-01`–`F-24`, `XW-01`–`XW-13` or any gate; did not execute any artifact, test or BlockHound run; did not run `sdd-design` or `sdd-apply`; did not modify the repository, specifications, ADRs or the real master architecture status; and did not commit, push, open a PR or merge. All decisions remain with the Developer/User.
