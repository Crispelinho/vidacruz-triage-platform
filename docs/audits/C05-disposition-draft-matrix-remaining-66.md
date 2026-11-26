# C-05 Draft Disposition Matrix — Remaining 66 Rows (OpenCode Proposals)

- **Status:** `OPENCODE DRAFT — NOT APPROVED — NOT SIGNED — NO DISPOSITION APPLIED — INTEGRATION NOT AUTHORIZED`.
- **Date:** 2026-11-25.
- **Scope:** the 66 rows still without a signed cell: A8–A10 (3), XW-01…XW-13 (13), D1–D7 (7), F-07…F-24 (18), ART-01…ART-16 (16), eight master columns (8), XW-14 (1). A1–A7 were disposed of one by one and are excluded from this draft.
- **Source:** `docs/audits/C05-consolidated-disposition-signature-package.md` §4 recommendations and §§5–11 evidence anchors, plus the independently verified source documents cited in each row.
- **Authority:** Developer/User only. The `OpenCode draft` column is a **recommendation prepared by OpenCode**, never a Developer/User disposition. Every `Developer decision` cell is blank.
- **Boundary:** this file does not close a finding, record a `D1`–`D7` node, accept/reject an ADR or gate, select an option, produce or execute an artifact, modify a document, authorize a test run, or authorize implementation. Signing a row is not acceptance of the underlying technical state.

## How to use this matrix

For each row the Developer/User writes exactly one value in the last column: `ACCEPT` | `REJECT` | `DEFER` | `NEEDS REVISION`. An empty cell means `NOT DECIDED`. `NEEDS REVISION` requires named content correction and an owner; it is not postponement. A batch approval is explicitly out of scope: each row is reviewed and disposed of individually.

---

## Cluster A — remaining divergences (3 rows)

| ID | OpenCode draft (proposed value) | Evidence | Risk if accepted without review | Developer decision |
| --- | --- | --- | --- | --- |
| A8 | `ACCEPT` of source `KEEP OPEN`: keep the two taxonomies separate | ADD-V2 line 111 lists pause, buffering, replay, replay-with-offset, partition-rotation; V3.1 §7 lines 248–251 define `F05-A/B/C`, lines 253–256 define `F06-A/B/C`, line 19 declares every option unselected | Addendum labels read as V3.1 options, implying a pause/buffering selection or an `F06-C` ADR-02 amendment | ________ |
| A9 | `ACCEPT` of source `CLOSE PROPOSED`: distinct namespaces by design | ADD-V2 C1–C5 lines 263–271; V3.1 §3 RC-1…RC-8 lines 77–90; MASTER DRAFT column 8 line 24 | The `PROVISIONAL` master schema approved by inference, or a contradiction/root-cause node treated as closed | ________ |
| A10 | `ACCEPT` of source `KEEP OPEN`: real placeholder gap, not filled here | ADD-V2 appendix lines 356–369 contain `[Usuario verifica contenido]` and `[Por completar]`; V3.1 §2 lines 25–62; UNIFIED §3 D10 line 133 | Placeholders filled by inference or traceability declared complete without an owner | ________ |

## Cluster B — XW independent verification (13 rows)

| ID | OpenCode draft (proposed value) | Evidence | Risk if accepted without review | Developer decision |
| --- | --- | --- | --- | --- |
| XW-01 | `NEEDS REVISION` of `ESCALATE TO DEVELOPER` | ADD-V2 lines 34–36 and 267; MATRIZ lines 132–149; ADR-02 lines 3, 23, 33; V3.1 F-12 line 48 | The V1 `ACCEPT` narrative becomes normative and ADR-02 is accepted by inference | ________ |
| XW-02 | `NEEDS REVISION` of `ESCALATE TO DEVELOPER` | ADR-03 lines 3, 141–156, 160–164, 166–174, 211–224; ADD-V2 lines 65–93; SPEC-C05 lines 621–628; UNIFIED D5 line 128 | Gate closure inferred from partial ADR-03 analysis; C-02/T-04 gate list left stale | ________ |
| XW-03 | `ACCEPT` of `KEEP OPEN`: unconfirmed label only | `10 vs 9 ADR` occurs only inside the register; checkable counts are 14 vs 12 | Count equivalence assumed and the delegating owner never supplies document, version and section | ________ |
| XW-04 | `ACCEPT` of `KEEP OPEN`: unconfirmed label only | Literal `B4`/`B5` absent from the authorized corpus except the register | `B4`/`B5` mapped to `F04-B` or global `D2` without a literal source | ________ |
| XW-05 | `NEEDS REVISION` of `ESCALATE TO DEVELOPER` | ADD-V2 lines 90–93, 279–284, 310–314; MATRIZ lines 100–103, 110–118; IMPL-ROAD lines 22, 39; SPEC-C05 lines 623–626 | Drafting scope read as implementation authorization or as a gate closure | ________ |
| XW-06 | `NEEDS REVISION` of `ESCALATE TO DEVELOPER` | ADD-V2 lines 180–222 and 289; MATRIZ line 112; V3.1 note line 246; UNIFIED §3 D1 line 124 and §4 lines 145–152 | Addendum `D2` mapped to V3 `D2` by inference instead of an approved crosswalk | ________ |
| XW-07 | `NEEDS REVISION` of `CLOSE PROPOSED` under P-04 | ADD-V2 lines 37, 113, 135, 267, 281, 298–305 versus ADR-03 Precision 1 line 160 and SPEC-C05 §3.1 line 267; replacement text drafted in the v2 instrument §4 P-04, not applied | Text applied without owner verification; pause-to-lease-TTL chain assumed removed; ADR-03 amended | ________ |
| XW-08 | `NEEDS REVISION` of `CLOSE PROPOSED` under P-04 | ADD-V2 lines 102, 110, 269; SPEC-C05 §5.1 lines 375–405 with line 404 an open gap, §3.1 lines 257–267, §3.3 lines 277–292; ADR-02 lines 56–75 | A pause-versus-buffering choice inferred or the open gap declared resolved | ________ |
| XW-09 | `NEEDS REVISION` of `CLOSE PROPOSED` under P-04 | ADD-V2 line 27 stale path and lines 234/238 role labels; ADR-ROAD §3 lines 49/53 and §3.2 line 64; SPEC-C05 line 434 | ADR status or retention ownership inferred from a path or role-label correction | ________ |
| XW-10 | `NEEDS REVISION` of `ESCALATE TO DEVELOPER` | ADD-V2 lines 4–8, 21, 327–332, 342; MATRIZ lines 3–5, 179–184; UNIFIED line 11 records PR #19 merged | A documentation merge treated as acceptance, and audit closure assigned without authority | ________ |
| XW-11 | `ACCEPT` of `KEEP OPEN`: evidenced edges confirmed, stronger claim not | ADD-V2 lines 153, 176, 270, 313; IMPL-ROAD G-09/G-10 lines 34–35; V3.1 §4.2 line 168; no literal ADR-08/C-06 to `D6` pairing | Stronger parallelism inferred from V3.1 L0 or from an unapproved dependency model | ________ |
| XW-12 | `NEEDS REVISION` of `ESCALATE TO DEVELOPER` | ADR-02 line 82; ADR-08 lines 3, 30–32, 36–38; SPEC-C05 lines 349–361 and header lines 3/12; ADR-ROAD lines 62–66 | Accepted Option A conflated with Proposed ADR-08, or acceptance inferred from a citation fix | ________ |
| XW-13 | `NEEDS REVISION` of `ESCALATE TO DEVELOPER` | ADD-V2 lines 198–206, 281–283, 295–308 and C2 line 268; V3.1 §4.1 lines 109–162 and §4.2 line 168; UNIFIED D4 line 127 | Addendum diagram and tables corrected to one chain without a Developer selection | ________ |

## Cluster C — global decision nodes (7 rows)

| ID | OpenCode draft (proposed value) | Evidence | Risk if accepted without review | Developer decision |
| --- | --- | --- | --- | --- |
| D1 | `NEEDS REVISION`: record `D1` before any equivalence rule is accepted | V3.1 invariant 7 line 21; F-01 line 37; edge 1 line 113; §7 `F01-A/B/C` lines 227–229; ART-01 verbatim extract | `F-01a` editorial label read as acceptance of `get()/join()/await()` equivalence | ________ |
| D2 | `NEEDS REVISION`: record the ADR-08 backend before store-specific guidance | ADR-08 Option A Redis+Lua Proposed, Option B PostgreSQL, Option C in-memory Rejected; V3.1 F-17 line 53 and J2a line 207; ART-03, ART-04, ART-06, ART-07, ART-10 | Redis assumed, or `F04-B` spring-kafka upgrade confused with global `D2` | ________ |
| D3 | `NEEDS REVISION`: define durability `D` and the rebuild boundary | V3.1 F-03 line 39, RC-2 line 82, edge 16 line 128; `F03-A/B/C` and `F02-A/B/C` evidence design; ART-04, ART-05, ART-06, ART-07, ART-10 | An unconditional replay-safety claim asserted while `D` is symbolic and three crash windows undefined | ________ |
| D4 | `NEEDS REVISION`: record the gap policy; do not select by inference | V3.1 edges 13–15 lines 125–127, 28 line 140, 47 line 159, edge 17 line 129, Lane T line 193, J3c line 212; `F05-A/B/C`, `F06-A/B/C` | Pause or buffering selected implicitly, strict gates relaxed, or J3c drafting halted | ________ |
| D5 | `NEEDS REVISION`: define terminal/replayable status, guard order and collision rule | V3.1 edges 18–19 lines 130–131; conditional `F03-B`, `F05-C`, `F06-A/B`; ART-07, ART-08, ART-09, ART-10, ART-11, ART-12 | DLT dedupe or check order asserted without a recorded status; `F-09b`/`F-10` gates bypassed | ________ |
| D6 | `NEEDS REVISION`: define the runtime/version posture at pinned 3.3.16 | V3.1 `F04-A/B/C` lines 242–244 and namespace note line 246; ART-02a factual report, ART-05, ART-08, ART-12, ART-13 | `F04-B` upgrade authorized without separate approval, or `awaitAsyncResultsOnStop` assumed present | ________ |
| D7 | `NEEDS REVISION`: define liveness thresholds and per-option convergence | V3.1 J5b line 217 and `F-16a…F-16d`; ART-09 conditional, ART-12, ART-13 | A uniform test passes vacuously, or J5b treated as closable by `D7` alone | ________ |

All `F01-A`…`F06-C` remain `UNSELECTED`; `F03-C`, `F04-B` and `F06-C` carry separate-authorization notes.

## Cluster D — P1/P2 findings (18 rows)

All rows below are `structurally verifiable = yes`, `definition pending`, `evidence pending execution`, `satisfied = no`. No draft closes a finding.

| ID | OpenCode draft (proposed value) | Evidence | Risk if accepted without review | Developer decision |
| --- | --- | --- | --- | --- |
| F-07 | `NEEDS REVISION` | V3.1 F-07; ART-10, ART-04, ART-05; acks value and dedupe store/key/TTL undefined | DLT confirmation inferred from a send return | ________ |
| F-08 | `NEEDS REVISION` | V3.1 F-08; ART-08, ART-05, ART-02, ART-13; three cancellation windows undefined | Cancellation guarantee asserted without thread dumps or offsets | ________ |
| F-09 | `NEEDS REVISION` | V3.1 F-09; ART-08, ART-05, ART-09, ART-10; handshake and pending key schema undefined | Revocation claimed without rebalance scenarios or persisted state | ________ |
| F-10 | `NEEDS REVISION` | V3.1 F-10; ART-11, ART-07, ART-09, ART-10; guard order and collision rule undefined | Expected `nivel`/`estado`/`contado` values invented | ________ |
| F-11 | `NEEDS REVISION` | V3.1 F-11; ART-10, ART-05, ART-08, ART-13; D4 scope and numeric backoff undefined | Evidence-driven backoff ceiling recorded as an executive decision | ________ |
| F-12 | `NEEDS REVISION` | V3.1 F-12; ART-01 text scan; exact quotation and reconciliation wording undefined | PoC wording read as implementation authorization | ________ |
| F-13 | `NEEDS REVISION` | V3.1 F-13; ART-16 publisher-only, ART-14 publisher; lease evidence unexecuted | Publisher-only label reused as consumer or ADR-03 evidence | ________ |
| F-14 | `NEEDS REVISION` | V3.1 F-14 and edge 20 line 132; ART-09; G-T25 citation and expected values open | E7 publisher ordering guarantee asserted as consumer proof | ________ |
| F-15 | `NEEDS REVISION` | V3.1 F-15; ART-14, ART-15, ART-13; marker, controls and D7 thresholds undefined | BlockHound detection presented as timed liveness evidence | ________ |
| F-16 | `NEEDS REVISION` | V3.1 F-16; ART-12, ART-13, ART-14, ART-15, ART-02; lifecycle inputs and run authorization open | One uniform test used across options; `F-16d` run treated as authorized | ________ |
| F-17 | `NEEDS REVISION` | V3.1 F-17 line 53 and J2a line 207; ART-03, ART-04; D2 backend and `D` undefined | Redis-specific text assumed or `D` defined without a backend | ________ |
| F-18 | `NEEDS REVISION` | V3.1 F-18; ART-02, ART-05; D6 version, commit-order predicate and `nack()` availability open | Commit-order assertions invented at the pinned version | ________ |
| F-19 | `NEEDS REVISION` | V3.1 F-19 line 55; ART-01 text scan; replacement wording and scan scope undefined | A global wait scan wrongly fails valid SPEC §4/R0 and MATRIX §1 text | ________ |
| F-20 | `NEEDS REVISION` | V3.1 F-20; ART-02, ART-13; module, command, spike outcome and D6 posture open | An executed dependency report treated as an official compatibility statement | ________ |
| F-21 | `NEEDS REVISION` | V3.1 F-21; ART-09, ART-13, ART-12; D4/D7, offsets and F-05 separation open | Pause rows declared verified without an executed pause/resume spike | ________ |
| F-22 | `NEEDS REVISION` | V3.1 F-22; ART-14, ART-15; pin/module, install order and JVM args undefined | Negative control skipped and a pass claimed | ________ |
| F-23 | `NEEDS REVISION` | V3.1 F-23; ART-15, ART-14; target set, filter mechanism and XML fields undefined | Unrelated tagged tests counted as target tests | ________ |
| F-24 | `NEEDS REVISION` | V3.1 F-24 line 60; ART-01 text scan; exact wording and F-07 ownership open, single owner J1 | ART-10 reused as F-24 evidence; ownership split across units | ________ |

Ten editorial parts (`F-12`, `F-13a`, `F-14a`, `F-15a`, `F-16a`, `F-17` text, `F-18` text, `F-19`, `F-20a`, `F-24`) have no owning artifact in the current 16 ART rows; Cluster E proposes the ART-01 correction-register scope to cover them.

## Cluster E — ART-01…ART-16 (16 rows)

Full draft replacement text for all 16 specifications is recorded in the local v2 working instrument §4 P-05; none of it is produced or executed. Defect legend: A ambiguous predicate, D dependency defect, U execution/authorization defect, R undue evidence reuse.

| ID | OpenCode draft (proposed value) | Evidence | Risk if accepted without review | Developer decision |
| --- | --- | --- | --- | --- |
| ART-01 | `NEEDS REVISION` | A,D,U,R; no named source or line range; no F-12/F-19/F-24 predicates; status `NOT PRODUCED` | Byte-exact scan applied globally and valid text wrongly failed | ________ |
| ART-02 | `NEEDS REVISION` | A,D,U; command and modules unnamed; D6 over-gates the factual report; status `NOT PRODUCED` | An unapproved "newer version" posture accepted from a fact report | ________ |
| ART-03 | `NEEDS REVISION` | A,D,U; requires an approved record while the record must be labelled not accepted; status `NOT PRODUCED` | D2 approval inferred from a draft record | ________ |
| ART-04 | `NEEDS REVISION` | A,D,U; TTL/retention inequality has no direction or units; status `NOT PRODUCED` | Unsourced numeric values adopted as decisions | ________ |
| ART-05 | `NEEDS REVISION` | A,D,U,R; F-03b predicate circular; wrong edge-44 citation; status `NOT EXECUTED` | One matrix reused to close F-02, F-11 and F-18 | ________ |
| ART-06 | `NEEDS REVISION` | A,D,U; defined baseline is the open D3 item; status `NOT PRODUCED` / `NOT EXECUTED` | A test run claimed against an undefined predicate | ________ |
| ART-07 | `NEEDS REVISION` | A,D,U; baseline and reset target undefined; status `NOT EXECUTED` | One-path rebuild compared as if two paths were executed | ________ |
| ART-08 | `NEEDS REVISION` | A,D,U,R; no window, command or generation token; status `NOT EXECUTED` | One observation reused for F-04, F-08 and F-09 | ________ |
| ART-09 | `NEEDS REVISION` | A,D,U,R; executable mechanism absent; five ACK states undefined; status `NOT EXECUTED` | A paused partition declared to deliver event 2 without a mechanism | ________ |
| ART-10 | `NEEDS REVISION` | A,D,U,R; unknown and dedupe undefined; D2–D5 over-gate E/D-only F-07; status `NOT PRODUCED` / `NOT EXECUTED` | ART-10 reused as F-24 evidence | ________ |
| ART-11 | `NEEDS REVISION` | A,D,U; guard order is an open D5 item; status `NOT PRODUCED` / `NOT EXECUTED` | Terminal `ALTA` asserted without expected values | ________ |
| ART-12 | `NEEDS REVISION` | A,D,U; cross-reference is not enumeration; D4/D5 absent from PASS; status `NOT PRODUCED` | A document mislabelled `NOT EXECUTED` or drafting over-gated | ________ |
| ART-13 | `NEEDS REVISION` | A,D,U,R; no numeric poll bound; rebalance clause circular; status `NOT EXECUTED` | A comparative test read as selecting an A/B/C/D option | ________ |
| ART-14 | `NEEDS REVISION` | A,U,R; pin, launcher and JVM flag omitted from PASS; status `NOT PRODUCED` / `NOT EXECUTED` | Publisher BlockHound output reused as consumer proof | ________ |
| ART-15 | `NEEDS REVISION` | A,U,R; target tests undefined; status `NOT PRODUCED` | Filtering proof presented as test correctness | ________ |
| ART-16 | `NEEDS REVISION` | A,U,R; publisher-only label absent; E7 in scope; status `NOT EXECUTED` | E7 ordering reused by a consumer predicate such as F-05, F-10, F-14b or F-21 | ________ |

## Cluster F — eight PROVISIONAL master columns (8 rows)

| ID | OpenCode draft (proposed value) | Evidence | Risk if accepted without review | Developer decision |
| --- | --- | --- | --- | --- |
| Column 1 ID / Artifact | `NEEDS REVISION` | Integrator review §3; local tokens are not decisions; UNIFIED §4 crosswalk | Legacy tokens silently merged into canonical IDs | ________ |
| Column 2 Status | `NEEDS REVISION` | ACCEPTED/CONDITIONAL/REJECTED reserved to Developer/User; `BLOCKED` describes evidence/runtime reliance | `BLOCKED` misapplied to an ADR or gate | ________ |
| Column 3 Normative source | `NEEDS REVISION` | Requires exact document, version, section and verbatim quote | A paraphrase promoted to a normative source | ________ |
| Column 4 Finding statement | `NEEDS REVISION` | Literal-statement rule; no reinterpretation | A finding silently reclassified | ________ |
| Column 5 Authority / owner | `ACCEPT` | Already fully compliant (auditor correction: draft previously marked it `NEEDS REVISION` with a contradictory reason); Developer/User decides, OpenCode investigates, Claude audits, integrator drafts | An agent recorded as a decision authority | ________ |
| Column 6 Dependencies | `NEEDS REVISION` | Draft adds a fifth kind `informational`; V3.1 §4 lines 98–105 has four kinds; seed rows misstate edges 1, 48 and 16 | Wrong edge kinds propagated into the master register | ________ |
| Column 7 Evidence / closure | `NEEDS REVISION` | Draft vocabulary differs from the V3.1 §9 four-state vocabulary | A criterion recorded as satisfied without evidence | ________ |
| Column 8 Cross-workstream trace | `NEEDS REVISION` | C1–C5 and RC-1–RC-8 distinct and open | A reference treated as closing a contradiction or selecting an option | ________ |

## Cluster G — new develop-state conflict (1 row)

| ID | OpenCode draft (proposed value) | Evidence | Risk if accepted without review | Developer decision |
| --- | --- | --- | --- | --- |
| XW-14 | `NEEDS REVISION` of `ESCALATE TO DEVELOPER` — keep open and escalated | ADR-02 at `0dee86b`: header line 3 still `Proposed. Not accepted.`; six proposal rows 122–127 against the text "5 proposals"; every row cites absent `adr-02-gap-resolutions.md`; open items warn that DLT escalation may break per-patient ordering | Numeric values for D3/D4 accepted by proposal, or a version posture selected between `F04-B` and reactor-kafka 1.3.19 without D6 | ________ |

---

## Draft totals

`ACCEPT` 7 (A8, A9, A10, XW-03, XW-04, XW-11, Column 5) · `NEEDS REVISION` 59 · `REJECT` 0 · `DEFER` 0 · signed 0 of 66.

## Auditor correction notes

Recorded by the independent audit (Claude) on the branch `docs/c05-draft-disposition-matrix-remaining-66`. These notes do not change any draft value except Column 5 above. They are findings for OpenCode to fix before the Developer decides.

1. **Cited V3.1 line numbers exceed the cited file.** `docs/audits/C05-v3.1-independent-review-and-dispositions.md` has 104 lines in this branch. The rows below cite V3.1 line numbers above 104. Each citation must be re-anchored to the correct line or removed. Rows that cite ADD-V2, UNIFIED or SPEC-C05 on the same line are listed only where the number is attributed to V3.1 in the row text.
   - A8: lines 248–251 (§7), 253–256 (§7).
   - XW-06: line 246 (V3.1 note).
   - XW-11: line 168 (§4.2).
   - XW-13: lines 109–162 (§4.1), 168 (§4.2).
   - D1: line 113 (edge 1), lines 227–229 (§7 `F01-A/B/C`).
   - D2: line 207 (J2a).
   - D3: line 128 (edge 16).
   - D4: lines 125, 127, 129, 140, 159, 193 (Lane T), 212 (J3c).
   - D5: line 130 (edges 18–19).
   - D6: lines 242–246 (`F04-A/B/C` and namespace note).
   - D7: line 217 (J5b).
   - F-14: line 132 (edge 20).
   - F-17: line 207 (J2a).
   The row for F-14 and F-17 were not sampled in full; the list is what the citation scan found.

1a. **Sources cited but absent from the repository.** `SPEC-C05` and `UNIFIED` are cited in rows A10, XW-02, XW-05, XW-06, XW-07, XW-08, XW-09, XW-10, XW-12 and XW-13. Neither file exists in this branch or on `origin/develop`. Those rows cannot be verified until the sources are committed under named paths.

1b. **Duplicate ADR files in `docs/adr/` on `develop`.** ADR-05 (two files), ADR-06 (two), ADR-07 (two) and ADR-09 (four). The matrix does not reference the duplicates, but any future citation of these ADRs is ambiguous. Not a finding against this matrix; raised for the ADR owner.
2. **Unversioned source for Cluster E.** The full replacement text for ART-01…ART-16 is recorded only in a "local v2 working instrument §4 P-05" that is not in the repository. It cannot be reviewed from this branch. Commit it under a named path, or delete the reference.
3. **Scope claim not in the row.** The note under Cluster D says Cluster E "proposes the ART-01 correction-register scope". The ART-01 row does not state that scope. Either add it to ART-01 or remove the claim.
4. **Verified citations.** Spot-checked and correct: ADR-02 lines 3, 82 and 122–127 (XW-14 header and proposal table) and ADR-08 lines 30–32 (Options A, B and C of D2).

**Restatement:** all values above are OpenCode recommendations. No cell is signed. No disposition, closure, acceptance, gate decision, artifact production, test run or integration is authorized by this file.
