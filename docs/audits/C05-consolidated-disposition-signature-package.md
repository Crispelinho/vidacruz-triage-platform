# C-05 Consolidated Disposition Signature Package

- **Status:** `PROPOSAL ONLY — OPEN — NOT APPROVED`. **No disposition in this package has been applied.**
- **Date:** 2026-11-25.
- **Prepared by:** OpenCode, C-05 specialist. Role limited to consolidating existing independent proposals into one signature instrument. No decision, closure, acceptance, rejection or authorization is exercised here.
- **Sole decision authority:** Developer/User. OpenCode investigates and drafts; Claude audits and registers contradictions; the integrator prepares recommendations. No agent may record a decision.
- **Repository context:** PR #24 merged into `develop` at `94fd42a` (2026-11-25T23:44:04Z). A later direct commit `b588152` added ADR-02 gap-resolution proposals. Per XW-10, merging documentation is **not** acceptance of its proposals, closure of findings/decisions/ADRs/gates, nor implementation authorization.
- **Baseline:** `develop` at `b588152` (`docs: Add ADR-02 gap resolution proposals`).
- **Sources consolidated (read-only):**
  - `docs/audits/C05-v3.1-independent-review-and-dispositions.md` (integrator) — 10 divergences, 8 master columns.
  - `docs/audits/C05-XW-independent-verification.md` (Claude auditor) — XW-01…XW-13.
  - `docs/audits/C05-developer-decision-packet.md` — D1–D7 and prerequisites C1–C18.
  - `docs/audits/C05-P1-P2-closure-and-ART-check.md` — F-07…F-24 and ART-01…ART-16.
  - `C:\Users\HP\reports\C05-v2-blocker-resolution-plan-v3.1.md` — V3.1 proposal, pending dictamen.
  - `MASTER-ARCHITECTURE-STATUS-draft-proposal.md` — eight `PROVISIONAL` columns.
  - `docs/adr/ADR-02-assistant-kafka-consumer.md` at `b588152` — new proposals, Cluster G.
- **Authorization boundary:** No repository file, ADR, specification, gate, roadmap or master register was modified to apply a disposition; no commit was made to record a decision; no test, BlockHound run or `sdd-*` command was executed; no implementation is authorized.

---

## 0. Signature protocol

For every row, the Developer/User writes **exactly one** value in the `Developer decision` column:

`ACCEPT PROPOSED` | `MODIFY (attach replacement text)` | `REJECT (state reason)` | `DEFER (state owner and date)`

1. An empty cell means `NOT DECIDED`. Silence, drafting, merge or recommendation is never acceptance.
2. `CLOSE PROPOSED` is a recommendation only. It does **not** close a finding, decision, ADR or gate.
3. `ESCALATE TO DEVELOPER` rows cannot be discharged by any agent; they require an explicit Developer/User record.
4. `KEEP OPEN` rows stay open until a source or decision is supplied by the named owner.
5. Approving V3.1, this package, or any cluster does **not** approve an option, gate, test, execution or repository change.
6. Numerical values proposed in Cluster G are not recorded values until the Developer/User explicitly accepts them under D3/D4.

---

## 1. Consolidated dashboard

| Cluster | Scope | Items | CLOSE PROPOSED | KEEP OPEN | ESCALATE / OPEN-RECORD | Dispositions applied |
| --- | --- | --- | --- | --- | --- | --- |
| A | V3.1 ↔ Addendum V2 divergences | 10 | 4 | 4 | 2 | 0 |
| B | XW-01…XW-13 verification | 13 | 3 | 3 | 7 | 0 |
| C | Global decision nodes D1–D7 | 7 | — | 7 `OPEN — unrecorded` | 7 require Developer record | 0 |
| D | P1/P2 findings F-07…F-24 | 18 | 0 | 18 `satisfied = no` | — | 0 |
| E | ART-01…ART-16 text corrections | 16 | 0 | 16 `NOT PRODUCED` / `NOT EXECUTED` | — | 0 |
| F | Master status columns | 8 | 0 | 8 `PROVISIONAL` | — | 0 |
| G | New develop-state ADR-02 proposals | 1 | 0 | 1 | 1 `POTENTIAL CONTRADICTION` | 0 |

Unchanged outside these clusters: `F-01`–`F-06` (P0, none satisfied), `F-01`–`F-24` identifiers/severities/references, ADR-02/ADR-03/ADR-08 `Proposed. Not accepted.`, G-T25 publisher-only record, all gates, and the `BLOCKED` demonstrated-viability status. Implementation: **`NOT AUTHORIZED`**.

---

## 2. Cluster A — Ten V3.1 ↔ Addendum V2 divergences

Source: integrator review §2. `CLOSE PROPOSED` = non-blocking for integration with the cited controlling text; no document is edited and no finding is closed.

| # | Topic | Documentary anchors | Proposed verdict | Rationale (proposed) | Developer decision |
| --- | --- | --- | --- | --- | --- |
| A1 (D1) | Option/decision ID namespace | ADD-V2 `CRUCE #5` lines 180–222; `Matriz de Contradicciones` C5 line 271; `Decisiones Pendientes — Informativas` lines 285–291; V3.1 §1 inv. 5 line 19, §7 note line 246, §10 corr. 6 line 360; UNIFIED §4 lines 143–152 | `ESCALATE TO DEVELOPER` | V3.1 is internally coherent but the cross-document `D2` collision is the highest integration risk; a unified ID register is an authority act. Mandatory read-only crosswalk until resolved. | ________ |
| A2 (D2) | Source and meaning of F-13 / F-14 | ADD-V2 `CRUCE #2` lines 56–77; V3.1 §2 lines 49–50, §4 edge 20 line 132, §6 J6 line 218; UNIFIED §2.2 lines 62–93 | `KEEP OPEN` | Addendum paraphrases and attributes to V1; V3.1 carries verbatim references and a different F-14b scope. Both strings preserved; no source rewritten. | ________ |
| A3 (D3) | Decision-authority wording | ADD-V2 lines 16–18, 252–259, 323; MATRIZ §4 lines 132–149, 153–159; V3.1 §5 line 192, §11 line 367; INT matrix M-7 line 21 | `CLOSE PROPOSED` | After INT-2/INT-3 both documents state: User/Developer decides, OpenCode investigates, integrator drafts/recommends, Claude audits. Former "integrator approval" wording is gone from V3.1. | ________ |
| A4 (D4) | Gap-policy dependency strength | ADD-V2 lines 113, 128–136, 279–283; MATRIZ §3.3 lines 110–118 and row 95 (`INFORMATIVA`); V3.1 §4 lines 98–105, 125–127, 140, 159, §5 line 193, §6 line 212 | `ESCALATE TO DEVELOPER` | Addendum contradicts itself (`INFORMATIVA` vs. blocking). V3.1 proposes a C-05-only model (`D4` gates guarantees, not drafting); reconciling roadmap semantics is authority-owned. | ________ |
| A5 (D5) | Meaning of `BLOCKED` | ADD-V2 `CRUCE #2 Hecho Verificado` lines 79–84; V3.1 header line 8 and §12 line 384; UNIFIED §3 D5 line 128 | `CLOSE PROPOSED` | Scope clarification only: V3.1 `BLOCKED` = demonstrated viability of the two V2 proposals (evidence unexecuted); addendum statement = ADR-03 closure. No status word changed. | ________ |
| A6 (D6) | ADR-02 contradiction framing | ADD-V2 lines 44–52, C1 line 267; V3.1 §1 inv. 7 line 21, §2 line 37, §7 lines 226–229, §11 line 367 | `KEEP OPEN` | Both documents leave the choice open (Proposed / Option B / reject). `F-01a` is explicitly not `D1` acceptance. `C1` is a recorded unresolved user decision. | ________ |
| A7 (D7) | Source reference for F-17 | ADD-V2 `CRUCE #4` lines 140–176; V3.1 §2 line 53, §6 J2a line 207, §3 RC-2 line 82 | `CLOSE PROPOSED` | Substantive classification converges (decision pending — backend selection). Only citation strings differ; addendum remains historical provenance. | ________ |
| A8 (D8) | Gap-policy option taxonomy | ADD-V2 line 111 (pause, buffering, replay, replay-with-offset, partition-rotation); V3.1 §7 lines 248–251, §1 inv. 5 line 19 | `KEEP OPEN` | Two different unselected taxonomies; addendum labels must not be read as V3.1 options. Reconciliation deferred. | ________ |
| A9 (D9) | Contradiction vs. root-cause namespaces | ADD-V2 C1–C5 lines 263–271; V3.1 §3 RC-1…RC-8 lines 77–90; MASTER DRAFT column 8 line 24 | `CLOSE PROPOSED` | `C1`–`C5` and `RC-1`–`RC-8` are distinct by design; dual trace suffices. Does **not** approve the MASTER DRAFT schema. | ________ |
| A10 (D10) | Traceability completeness | ADD-V2 appendix lines 356–369 (`[Usuario verifica contenido]`, `[Por completar]`); V3.1 §2 lines 25–62; UNIFIED §3 D10 line 133 | `KEEP OPEN` | Real open documentation gap in the addendum. V3.1 has complete references; this package neither fills placeholders nor modifies an existing document. | ________ |

**Proposed counts:** `CLOSE PROPOSED` 4 (A3, A5, A7, A9) · `KEEP OPEN` 4 (A2, A6, A8, A10) · `ESCALATE` 2 (A1, A4). Applied: **0**.

---

## 3. Cluster B — XW-01…XW-13 independent verification

Source: Claude auditor report. All 13 rows remain OPEN in the register; verdicts are proposals only.

| ID | Literal-source result | Class verified | Proposed verdict | Authority / evidence required | Developer decision |
| --- | --- | --- | --- | --- | --- |
| XW-01 | YES — ADD-V2 lines 34–36, 267; MATRIZ lines 132–149; ADR-02 line 3, 23, 33; V3.1 F-12 line 48 | `POTENTIAL CONTRADICTION` | `ESCALATE TO DEVELOPER` | Explicit ADR-02 status decision + corrected C1 row. No V1 `ACCEPT` narrative as norm. | ________ |
| XW-02 | YES — ADR-03 line 3, 141–156, 160–164, 166–174, 211–224; ADD-V2 lines 65–93; SPEC-C05 lines 621–628; UNIFIED D5 line 128 | `POTENTIAL CONTRADICTION` + `CROSS-WORKSTREAM DEPENDENCY` | `ESCALATE TO DEVELOPER` | Record naming which sub-decisions close which gates; update C-02/T-04 gate list. | ________ |
| XW-03 | NO — "10 vs 9 ADR" found only inside the register. Checkable counts are 14 vs 12, a different claim | Unconfirmed label only | `KEEP OPEN` | Delegating owner supplies document, version and section of the literal statement. No count equivalence inferred. | ________ |
| XW-04 | NO — literal `B4`/`B5` absent from the authorized corpus except the register | Unconfirmed label only | `KEEP OPEN` | Literal source with version and section. `B4`/`B5` are **not** inferred to be `F04-B`/global `D2`. | ________ |
| XW-05 | YES — ADD-V2 lines 90–93, 279–284, 310–314; MATRIZ lines 100–103, 110–118; IMPL-ROAD lines 22, 39; SPEC-C05 lines 623–626 | `POTENTIAL CONTRADICTION` + `CROSS-WORKSTREAM DEPENDENCY` | `ESCALATE TO DEVELOPER` | Written scope decision separating drafting from implementation under open variables; gate updates. | ________ |
| XW-06 | YES — ADD-V2 lines 180–222, 289; MATRIZ line 112; V3.1 §7 note line 246; UNIFIED §3 D1 line 124, §4 lines 145–152 | `POTENTIAL CONTRADICTION` | `ESCALATE TO DEVELOPER` | Approved ID register/crosswalk + corrected merged labels. No addendum-`D2` → V3-`D2` mapping by inference. | ________ |
| XW-07 | YES, both sides — ADD-V2 lines 37, 113, 135, 267, 281, 298–305 vs. ADR-03 §Precision 1 line 160 and SPEC-C05 §3.1 line 267 | `POTENTIAL CONTRADICTION` | `CLOSE PROPOSED` (text-only, not applied) | Owner-approved editorial correction tying lease only to publisher publication budget. No ADR-03 amendment or new evidence needed. | ________ |
| XW-08 | YES — ADD-V2 lines 102, 110, 269; SPEC-C05 §5.1 lines 375–405 (line 404 real gap), §3.1 lines 257–267, §3.3 lines 277–292; ADR-02 lines 56–75 | `POTENTIAL CONTRADICTION` | `CLOSE PROPOSED` (text-only, not applied) | Corrected citation. No pause-versus-buffering choice implied. | ________ |
| XW-09 | YES — ADD-V2 line 27 stale path, lines 234/238 role labels; ADR-ROAD §3 lines 49/53, §3.2 line 64; SPEC-C05 line 434 | `POTENTIAL CONTRADICTION` | `CLOSE PROPOSED` (text-only, not applied) | Corrected path and ADR-10/ADR-14 role labels. No status or retention-ownership decision inferred. | ________ |
| XW-10 | YES — ADD-V2 lines 4–8, 21, 327–332, 342; MATRIZ lines 3–5, 179–184; UNIFIED line 11 (PR #19 merged) | `POTENTIAL CONTRADICTION` | `ESCALATE TO DEVELOPER` | Explicit decision on whether merge constitutes acceptance and who owns "Claude closes audit". | ________ |
| XW-11 | PARTIAL — ADD-V2 lines 153, 176, 270, 313; IMPL-ROAD G-09/G-10 lines 34–35; V3.1 §4.2 line 168. No literal ADR-08/C-06 ↔ `D6` pairing | `CROSS-WORKSTREAM DEPENDENCY` confirmed for evidenced edges; stronger parallelism unconfirmed | `KEEP OPEN` | Literal source for the claimed parallelism; Developer approval of dependency model. No inference from V3.1 L0. | ________ |
| XW-12 | PARTIAL but sufficient — ADR-02 line 82; ADR-08 line 3, 30–32, 36–38; SPEC-C05 lines 349–361, header 3/12; ADR-ROAD lines 62–66 | `CROSS-WORKSTREAM DEPENDENCY` | `ESCALATE TO DEVELOPER` | Authoritative record separating accepted Option A from Proposed ADR-08; corrected ADR-02 line 82. No acceptance by inference. | ________ |
| XW-13 | YES — ADD-V2 lines 198–206, 281–283, 295–308, C2 line 268; V3.1 §4.1 lines 109–162, §4.2 line 168; UNIFIED D4 line 127 | `POTENTIAL CONTRADICTION` | `ESCALATE TO DEVELOPER` | Developer selects one authoritative chain and authorizes correction of the addendum diagram/tables. V3.1 DAG does not select for the addendum. | ________ |

**Proposed counts:** `CLOSE PROPOSED` 3 (XW-07, XW-08, XW-09) · `KEEP OPEN` 3 (XW-03, XW-04, XW-11) · `ESCALATE` 7 (XW-01, XW-02, XW-05, XW-06, XW-10, XW-12, XW-13). Applied: **0**.

---

## 4. Cluster C — Global decision nodes D1–D7

Every row is literally `OPEN — unrecorded`. These are questions, not authorizations.

| Node | Exact question | Available tokens | Open consequence / boundary | Required evidence | Literal status | Developer decision |
| --- | --- | --- | --- | --- | --- | --- |
| D1 | Accept the hardened rule treating `get()`, `join()`, `await()` as equivalent to ADR-02's literal `.block()` prohibition? | `F01-A`, `F01-B`, `F01-C` | `F-01a` label is independent and never accepts `D1`; only `F-01b` waits on `D1` (edge 1). Ambiguous baseline otherwise. | ART-01 verbatim extract + textual scan; label pass not equivalence acceptance | `OPEN — unrecorded` | ________ |
| D2 | Which projection/idempotence backend does ADR-08 select? **Global `D2` = ADR-08 backend only**; spring-kafka upgrade is `F04-B`. | ADR-08 Option A Redis+Lua (Proposed), B PostgreSQL, C in-memory (Rejected in ADR text) | Store-specific Lua/`ProcessedEvent` guidance unsafe until backend named; `D2 → D3` is an approval edge. | ART-03, ART-04, ART-06, ART-07, ART-10 | `OPEN — unrecorded` | ________ |
| D3 | What exactly is durability `D`: persistence mode, fsync/replication ack, marker TTL vs. Kafka retention, rebuild boundary, C4 predicate? | `F03-A`, `F03-B`, `F03-C` (separate authorization) + `F02-A/B/C` evidence design | No unconditional "replay is safe" claim; three crash windows undefined; `D5` recording waits on `D3`. | ART-04, ART-05, ART-06, ART-07, ART-10 | `OPEN — unrecorded` | ________ |
| D4 | What gap policy governs `1 → 3 → 2`: pause vs. buffered frontier, scope, pending recovery, admissible mechanism? | `F05-A/B/C`, `F06-A/B/C` (`F06-C` needs separate authorization) | Pause row not executable while partition is truly paused; buffering can relax C4. `F-05b`, `F-06c`, `F-09b`, `F-11b`, `F-21` strict-gated. | ART-05, ART-08, ART-09, ART-10, ART-12, ART-13 | `OPEN — unrecorded` | ________ |
| D5 | Are `GAP_UNRESOLVED` / DLT records terminal or replayable, with what guard order and collision rule? | No exclusive `F0x` option; conditional `F03-B`, `F05-C`, `F06-A/B` | Terminal/replayable status drives collision outcome, DLT dedupe, check order. `F-09b`, `F-10` wait strict. | ART-07, ART-08, ART-09, ART-10, ART-11, ART-12 | `OPEN — unrecorded` | ________ |
| D6 | What runtime/version posture governs shutdown and cancellation, including presence of `awaitAsyncResultsOnStop`? | `F04-A`, `F04-B` (upgrade, separate authorization, **not** global `D2`), `F04-C` | At pinned 3.3.16 no mechanism for "never re-commit after revocation"; hook name and DLT-future propagation undefined. | ART-02a factual report, ART-05, ART-08, ART-12, ART-13 | `OPEN — unrecorded` | ________ |
| D7 | What numeric liveness thresholds and acceptable spike result apply, and which per-option convergence assertion is valid? | No `F0x` option; `F-16a`–`F-16d` are subcomponents | A uniform test can pass vacuously. J5b needs D6, F-04, F-08, F-09, F-22, F-23, F-15b — not D7 alone. | ART-09 (conditional), ART-12, ART-13 | `OPEN — unrecorded` | ________ |

All `F01-A`…`F06-C` remain **UNSELECTED**. `F03-C`, `F04-B`, `F06-C` carry explicit extra-authorization notes and are not gate closures.

---

## 5. Cluster D — P1/P2 findings F-07…F-24

All 18 rows: `structurally verifiable = yes`, `definition pending`, `evidence pending execution`, `satisfied = no`. Supporting artifacts are **not executed**. No finding is closed.

| ID | Sev | Future closure criterion (condensed) | Supporting artifacts | Still undefined | States | Developer decision |
| --- | --- | --- | --- | --- | --- | --- |
| F-07 | P1 | Per-option `DLT_CONFIRMED` predicate (acks, transaction, read-back); timeout = unknown-not-confirmed; separate projection/DLT dedupe; DLT fault test | ART-10, ART-04, ART-05 | confirmation mechanism, acks value, dedupe store/key/TTL, replay effect | yes / pending / pending / no | ________ |
| F-08 | P1 | Cancellation table for three windows (effect→stop, DLT-confirmed→stop, ack→stop) with thread dumps, offsets, recovery record | ART-08, ART-05, ART-02, ART-13 | per-operation fate, DLT future propagation, F-04 hook, recovery format | yes / pending / pending / no | ________ |
| F-09 | P1 | Revocation handshake: stop acks, cancel DLT, flush/discard pending, persist keyed state; rebalance scenarios | ART-08, ART-05, ART-09, ART-10 | handshake, pending key schema, D4 policy, D5 status | yes / pending / pending / no | ________ |
| F-10 | P1 | Guard order, deterministic collision rule, `GAP_UNRESOLVED` status, DLT metadata; `nivel`/`estado`/`contado` assertions | ART-11, ART-07, ART-09, ART-10 | guard order, collision rule, D5 status, expected values | yes / pending / pending / no | ________ |
| F-11 | P1 | Durable DLT-unavailable state per policy with attempt count, backoff, pause scope, frontier, rebalance, cleanup | ART-10, ART-05, ART-08, ART-13 | D4 scope, numeric backoff, retry schema, alert predicate | yes / pending / pending / no | ________ |
| F-12 | P1 | Both V2 documents record verbatim `Proposed`/Option B/fallback exclusion; MATRIX §4.2 line 121 reconciled; no implied PoC authorization | ART-01 text scan | exact quotation, reconciliation wording, PoC removal | yes / pending / pending / no | ________ |
| F-13 | P1 | SPEC §2 R2 cites ADR-03 Precision 1 and §3.1/§7.10 lease invariant; E7 relabelled publisher-only; external ADR-03 pack | ART-16 (publisher-only), ART-14 publisher | lease evidence unexecuted, label owner, acceptance boundary | yes / pending / pending / no | ________ |
| F-14 | P1 | E7 stops claiming unaccepted publisher ordering guarantee; consumer `1 → 3 → 2` test with `nivel`/count/state; ART-16 barred | ART-09 | G-T25 scope citation, expected values, F-10/D5 separation | yes / pending / pending / no | ________ |
| F-15 | P1 | Detection/liveness split: BlockHound on marked listener threads vs. separate timed test; negative control required | ART-14, ART-15, ART-13 | marker, controls, D7 thresholds, marked thread | yes / pending / pending / no | ________ |
| F-16 | P1 | F-16a split, F-16b held thread/poll instrumentation/terminal state, F-16c bounded convergence, F-16d effective settings and XML counts; no uniform test | ART-12, ART-13, ART-14, ART-15, ART-02 | lifecycle inputs, D6, D7, harness proof, run authorization | yes / pending / pending / no | ________ |
| F-17 | P1 | Store-neutral wording or explicit conditioning on approved ADR-08 backend; `D` defined against that backend | ART-03, ART-04 | D2 backend, `D`, replacement text | yes / pending / pending / no | ________ |
| F-18 | P1 | MATRIX §4 states manual/async out-of-order commit behaviors and §5.2 frontier link; mixed-outcome test | ART-02, ART-05 | D6 version, commit-order predicate, `nack()` availability | yes / pending / pending / no | ________ |
| F-19 | P1 | MATRIX §4.1 uses only serialized/reactive-composition wording; no strongest/better/best language. **Text-only; no runtime evidence** | ART-01 text scan | replacement wording, scan scope | yes / pending / pending / no | ________ |
| F-20 | P1 | Upstream Spring lines separated from pinned spring-kafka 3.3.16/Boot 3.5.16; executed dependency report; B compatibility = empirical spike | ART-02, ART-13 | module/command, spike outcome, D6 posture, no official compatibility statement exists | yes / pending / pending / no | ________ |
| F-21 | P2 | Both pause rows read "candidate; unverified at resolved client version"; executed pause/resume spike with other partitions continuing | ART-09, ART-13, ART-12 | D4 policy, D7 threshold, offsets, F-05 separation | yes / pending / pending / no | ________ |
| F-22 | P2 | E2/E3 and MATRIX §5 record pin/module, launcher, install order, JVM flag in both `Test` tasks, negative control before any pass | ART-14, ART-15 | pin/module, install mechanism, JVM args, control paths | yes / pending / pending / no | ________ |
| F-23 | P2 | MATRIX §5 cross-references SPEC E8; tag filter proven; JUnit XML shows non-zero target-test count | ART-15, ART-14 | target set, filter mechanism, XML fields | yes / pending / pending / no | ________ |
| F-24 | P2 | SPEC §5/§10 state "state name defined; mechanism open pending F-07". **Editorial only, single owner J1**; ART-10 must not be F-24 evidence | ART-01 text scan | exact wording, F-07 ownership | yes / pending / pending / no | ________ |

Ten editorial parts (`F-12`, `F-13a`, `F-14a`, `F-15a`, `F-16a`, `F-17` text, `F-18` text, `F-19`, `F-20a`, `F-24`) have no owning artifact as the 16 ART rows are currently written; Cluster E proposes the ART-01 correction-register scope to fix that.

---

## 6. Cluster E — ART-01…ART-16 proposed text corrections

Legend: **A** ambiguous PASS/FAIL predicate · **D** dependency omitted/misassigned/over-gated · **U** execution/authorization defect · **R** undue evidence reuse. All 16/16 have a proposed correction; **none was produced, modified or executed**.

| ART | Defects | Detected problem (condensed) | Proposed correction (condensed) | Literal status | Developer decision |
| --- | --- | --- | --- | --- | --- |
| ART-01 | A,D,U,R | "Byte-exact" has no source path/line; global wait scan would wrongly fail valid SPEC §4/R0 and MATRIX §1 text; F-12/F-19/F-24 predicates absent; "developer review note" undefined | Named source + range; R1 label; whitelist consumer-path only; MATRIX §4.1 scan; F-24 label; new correction register, V1/V2 unchanged; D1 only for equivalence clause | `NOT PRODUCED` | ________ |
| ART-02 | A,D,U | Command/modules unnamed; "newer version" implies unapproved acceptance; B spike outcome absent; D6 over-gates factual report | Split ART-02a factual (no D6) / ART-02b posture (D6); separate BOM values; report execution separately authorized | `NOT PRODUCED` | ________ |
| ART-03 | A,D,U | Requires "approved record" while record must be labelled not accepted; requires SPEC edit forbidden by V3.1 §9 | Draft record + proposed replacement text; label "not accepted until D2 recorded and ADR-08 approved"; D2 gates acceptance only | `NOT PRODUCED` | ________ |
| ART-04 | A,D,U | TTL/retention inequality has no direction/units; presupposes D2; values unsourced | Numeric values with units and named config source; relation `marker TTL ≤ replay window ≤ Kafka retention` or recorded alternative; `D` non-symbolic | `NOT PRODUCED` | ________ |
| ART-05 | A,D,U,R | Projection predicate is circular (F-03b open); wrong edge-44 citation; D5 missing for case 10; multi-finding reuse | All ten F-02 rows executed; state-to-observable map; reference ART-06/F-03b predicate; correct dependencies; F-11/F-18 need own clauses | `NOT EXECUTED` | ________ |
| ART-06 | A,D,U | "Defined baseline under numeric predicate" is the open D3 item — circular; injection config absent | Split ART-06a definition / ART-06b execution; predicate document then three recorded windows | `NOT PRODUCED` / `NOT EXECUTED` | ________ |
| ART-07 | A,D,U | "Recorded baseline" undefined; reset target absent; D5 variant in minimum content but not PASS | Both paths executed; markers cleared; offsets reset to last committed offset; baseline equality or recorded `EventoHistorico` fallback; D5 only for variant | `NOT EXECUTED` | ________ |
| ART-08 | A,D,U,R | No observation window/command/generation token; hook named but not exercised; three F-08 windows implicit; one test reused for three findings | Three windows + late ack/commit/DLT; generation token; thread dumps; offsets via named command; separate F-04/F-08/F-09 assertions; ART-02a version | `NOT EXECUTED` | ________ |
| ART-09 | A,D,U,R | "Named mechanism" need not be executable; "3 exactly once" conflicts with open duplicate count; F-14b/F-21 clauses absent; D7 misused for F-05 | D4-recorded mechanism executed with exact offsets; five ACK states; `1 → 3 → 2` expected values; D7 only for F-21 clause; ART-16 inadmissible | `NOT EXECUTED` | ________ |
| ART-10 | A,D,U,R | "Unknown"/dedupe undefined; D2–D5 mandatory over-gates `E/D-only` F-07; listed as F-24 evidence | Split ART-10a definition (no D gate) / ART-10b execution; supports F-07/F-11 only; F-24 closed by ART-01 label | `NOT PRODUCED` / `NOT EXECUTED` | ________ |
| ART-11 | A,D,U | Guard order/collision rule is open D5 item — circular; no concrete `ALTA` assertion | Split ART-11a definition / ART-11b execution; expected `nivel`/`estado`/`contado`; no terminal `ALTA` violation; order independence | `NOT PRODUCED` / `NOT EXECUTED` | ________ |
| ART-12 | A,D,U | Cross-reference not enumeration; D4/D5 absent from PASS; "F-16a exists" undefined; NOT EXECUTED mislabels a document; drafting over-gated | Enumerate hook, terminal state, convergence, applicability, D7/D6 sources, ART-14/15 and F-16a/b IDs; drafting permitted; status `NOT PRODUCED` | `NOT PRODUCED` | ________ |
| ART-13 | A,D,U,R | No numeric poll bound; "rebalances satisfy F-04/F-09" circular; could read as selecting A/B/C/D; harness prerequisites missing | Per-option held thread/poll instrumentation/effective settings; numeric bound from ART-12/D7; ART-08/09 assertions; comparative only, selects no option; no authorization | `NOT EXECUTED` | ________ |
| ART-14 | A,U,R | PASS checks controls but omits pin/module, launcher, install order, JVM flag, marker; conflates spec with execution; publisher reuse allowed | Split ART-14a specification / ART-14b execution; publisher and consumer reports separate files; publisher BlockHound is harness control only | `NOT PRODUCED` / `NOT EXECUTED` | ________ |
| ART-15 | A,U,R | "Target tests" undefined; unrelated tagged tests could be counted; config reading conflated with demonstration run | Named `integration` tag; JUnit XML `tests > 0` for ART-13/14 target set; run separately authorized; proves filtering only | `NOT PRODUCED` | ________ |
| ART-16 | A,U,R | Label not required on artefact; no ban on cross-references; `ACK before publicado_en` and `eventId` dedupe omitted; E7 in scope invites reuse | Artefact labelled "publisher-only"; E7 out of scope; no consumer predicate cites it; ART-14b publisher block referenced not duplicated; ADR acceptance outside D1–D7 | `NOT EXECUTED` | ________ |

Most problematic node assignments: **D3, D5** (circular predicates); **D4/D7** (F-05 vs. F-21 conflation); **D6** (decision attached to factual report); **D2** ("approved record" contradiction). Definition records should be `NOT PRODUCED`; run-based records stay `NOT EXECUTED`.

---

## 7. Cluster F — Eight PROVISIONAL master columns

Source: integrator review §3. All eight remain `PROVISIONAL`; no real `MASTER-ARCHITECTURE-STATUS.md` was created or updated.

| # | Column | Authority check | Proposed alignment (recommendation only) | Disposition | Developer decision |
| --- | --- | --- | --- | --- | --- |
| 1 | `ID / Artifact` | Neutral; local tokens are not decisions | Keep; one row = one stable ID; legacy tokens only via UNIFIED §4 crosswalk | `PROVISIONAL` | ________ |
| 2 | `Status` | Vocabulary restricts ACCEPTED/CONDITIONAL/REJECTED to Developer/User | Append: `BLOCKED` describes evidence/runtime reliance only, never ADR/G-T25 status | `PROVISIONAL` | ________ |
| 3 | `Normative source / citation` | Requires exact document/version/section, verbatim quotes | Keep; require version label and section/line | `PROVISIONAL` | ________ |
| 4 | `Finding / decision statement` | Literal statement, no reinterpretation | Keep as drafted | `PROVISIONAL` | ________ |
| 5 | `Authority / owner` | **Fully compliant** — Developer/User decides, OpenCode investigates, Claude audits, integrator drafts; `D1`–`D7` may not be recorded by agents | Keep verbatim; controlling authority column | `PROVISIONAL` | ________ |
| 6 | `Dependencies / blocking behaviour` | **Not compliant** — adds `informational` outside V3.1 §4 four kinds; three seed-row edge kinds are wrong (ADR-02 `approval` vs. edge 1 `strict`; ADR-03 `evidence` vs. edge 48 `strict`; D3 `F-03b → D5` vs. edge 16 `D3 → D5`) | Restrict to `strict`/`partial`/`approval`/`evidence`; carry `INFORMATIVA` only as separately labelled crosswalk annotation; correct three seed rows | `PROVISIONAL` | ________ |
| 7 | `Evidence / closure criterion` | **Vocabulary mismatch** — draft uses `not executed`/`definition pending`/`satisfied`; V3.1 §9 uses four states | Adopt `structurally verifiable` / `definition pending` / `evidence pending execution` / `satisfied` verbatim | `PROVISIONAL` | ________ |
| 8 | `Cross-workstream trace / contradiction ref` | Consistent; `C1`–`C5` and `RC-1`–`RC-8` distinct and open | Keep; add that a reference does not close a contradiction or select an option | `PROVISIONAL` | ________ |

---

## 8. Cluster G — New develop-state ADR-02 proposals (post-PR #24)

New entry registered because `develop` advanced after the four reports were merged. This is a **new proposal only**; no existing ID was reclassified or closed.

- **Source:** `docs/adr/ADR-02-assistant-kafka-consumer.md`, document version = `develop` at commit `b588152`, sections: header status line 3; `Configuration Proposals Under Review` lines 111–141 (table rows 122–126); `Open Items` lines 143–151.
- **Classification:** `POTENTIAL CONTRADICTION` (document, version and section cited above).
- **Literal facts:** ADR-02 remains `Proposed. Not accepted.` (line 3). The document adds numeric proposals: reactor-kafka `1.3.19` (line 122), `ProcessedEvent` TTL `37 days` (line 123), Redis `RDB+AOF, fsync=everysec, replication=1, ack-level=replica` (line 124), gap timeout `60 seconds` (line 125), pending-map `1000 entries per patient` (line 126), DLT topic + retries `3` with `1s → 2s → 4s` (line 127). All are explicitly labelled "NOT yet accepted decisions" pending technical validation.
- **Conflicts observed:**
  1. Proposals supply values for `D3` (TTL, durability, retention) and `D4` (timeout, pending-map) while both nodes are `OPEN — unrecorded`. A value cannot be recorded by proposal.
  2. `F04-B` is the unselected spring-kafka 4.2+ upgrade; proposing the discontinued/archived reactor-kafka `1.3.19` adds a third version path that V3.1 §7 has no token for. `F-20` records that no official compatibility statement exists; D6 is unrecorded.
  3. The text says "5 proposals" (lines 3 and 113) while the table contains 6 decision rows (lines 122–127).
  4. Every table row cites `adr-02-gap-resolutions.md`, which is **absent** from the repository at `b588152` (verified by `git ls-tree -r`).
  5. The open-item text warns DLT escalation on timeout/full may break per-patient event ordering — directly the `D4`/`F-05`/`F-06` open question; no mitigation is recorded.
- **Proposed verdict:** `ESCALATE TO DEVELOPER`. Required for any real closure: (a) explicit Developer/User acceptance of each numeric value under `D3`/`D4`; (b) a version posture decision under `D6` distinguishing `F04-B` from the reactor-kafka proposal; (c) correction of the 5-vs-6 count and the dangling citation; (d) no ADR-02 status inference. **Applied: 0.**

| New ID | Classification | Proposed verdict | Developer decision |
| --- | --- | --- | --- |
| `XW-14` (NEW — proposed, not in original register) | `POTENTIAL CONTRADICTION` — ADR-02 at `b588152`, lines 3, 111–141, 143–151 | `ESCALATE TO DEVELOPER` | ________ |

---

## 9. Consolidated signature record

| Item | Literal state |
| --- | --- |
| V3.1 developer dictamen | `PENDING — NOT APPROVED` (`APPROVE` / `APPROVE WITH CONDITIONS` / `REQUIRES REVISION`) |
| Cluster A dispositions signed | `NO` — 0/10 |
| Cluster B dispositions signed | `NO` — 0/13 |
| Cluster C nodes recorded | `NO` — 0/7 (`OPEN — unrecorded`) |
| Cluster D findings closed | `NO` — 0/18 (`satisfied = no`) |
| Cluster E artifacts produced/executed | `NO` — 0/16 |
| Cluster F columns approved | `NO` — 0/8 (`PROVISIONAL`) |
| Cluster G new conflict signed | `NO` — 0/1 |
| Findings F-01…F-24 | `OPEN` — no reclassification, severities and references unchanged |
| P0 criteria | None `satisfied` |
| ADR-02 / ADR-03 / ADR-08 / G-T25 | Unchanged; no acceptance, rejection or amendment |
| Gates G-01…G-12 / C-05 completion | `OPEN` |
| ART-01…ART-16 | `NOT PRODUCED` / `NOT EXECUTED` |
| Real master architecture register | Not written |
| Implementation authorization | `NOT GRANTED` |
| **Developer/User signature** | `______________________________` |
| **Date** | `______________________________` |

---

## 10. Boundary restatement

- No finding, decision, contradiction, ADR, gate, roadmap or master column was closed, accepted, rejected, reclassified or amended.
- No option was selected: no A/B/C/D, no `F01-A`…`F06-C`, no `F03-C`/`F04-B`/`F06-C`, no pause-versus-buffering policy.
- No test, fault injection, BlockHound run or `sdd-*` command was executed; no implementation was performed.
- No repository file, ADR, specification or existing report was modified by this package; the only change is the addition of this new document.
- No commit was made to record a decision; no merge was performed. All authority remains with the Developer/User.
