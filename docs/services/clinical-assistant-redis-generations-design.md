# Clinical Assistant: Redis Projection Rebuild by Generations (Design Proposal)

**Status:** Proposed. Not accepted. No implementation.
**Service:** Clinical Assistant (projection `EstadoUrgenciaProyeccion`, dedupe `ProcessedEvent`).
**Gates involved:** CA-G-11 (TTL of `ProcessedEvent` and Kafka retention), CA-G-24 (rebuild completeness), and Redis durability (gap identifier to be confirmed). Open.
**Depends on:** OD-3 and OD-5 (`EventoHistorico`), G-03 (`pacienteSequence`), DEC-03 (envelope), RF-06 (whether alerts are part of the history).
**Scope:** design of the protocol only. No changes to D0, D1, DEC-11, code or versions.

---

## 1. Status of assumptions

Each assumption is labelled. Nothing in this document is a verified guarantee.

| ID | Assumption | Status | Evidence required |
|---|---|---|---|
| A1 | `EventoHistorico` contains every event needed to rebuild the projection | **Not verified.** Proposed source, decided only on branch `feature/od-3-4-decisions` | Schema, migrations, and a rebuild test against real data |
| A2 | The history row is written in the same transaction as the state change and the outbox row | **Not verified.** Design claim | Transactional code and an integration test that forces a failure between writes |
| A3 | Each event carries `eventId`, `pacienteSequence`, type, envelope version and payload | **Partly documented.** DEC-03 is *Proposed* | Envelope code and a schema-version test |
| A4 | `pacienteSequence` is unique per patient and assigned atomically | **Not decided.** G-03 | Sequence code in the same transaction; unique constraint test |
| A5 | Kafka retains `triaje.eventos` for the effective retention period | **Not decided.** On `develop`, OD-3 is open and the retention value is not decided. On `feature/od-3-4-decisions` (not merged) there is a documentary 30-day decision. No executable Kafka retention configuration is verified in any branch. The 30 days are a working value, not an accepted decision on `develop`. | Topic configuration in the target environment |
| A6 | Alerts (RF-06) are or are not part of the reconstructible history | **Not decided** | Decision recorded in RF-06 |
| A7 | Redis persistence (AOF or RDB) preserves generations across restarts | **Not decided.** Redis durability (gap identifier to be confirmed) | Persistence configuration and a restart test |

## 2. Invariants the design must keep

- **I1.** The active generation is never partially built. Readers see either the old complete generation or the new complete generation.
- **I2.** Within one generation, each `eventId` is applied at most once.
- **I3.** Within one generation, events for a patient are applied in `pacienteSequence` order (depends on A4).
- **I4.** Every event at or before the cut point comes from the history. Every event after the cut point comes from live consumption. No event is in both sources without deduplication.
- **I5.** Activation is a single atomic change of the active-generation pointer.
- **I6.** The previous generation is kept until the rollback window closes. A reader that read the old pointer finishes reading the old generation.
- **I7.** A failed or interrupted rebuild leaves no effect on the active generation.

## 3. Structure

- **Active generation:** the keys that serve API-05. Identified by a pointer key (for example `vidacruz:projection:active`) that holds the generation id.
- **Candidate generation:** keys under a new generation id, built during a rebuild. Not visible to readers.
- **Dedupe markers:** `ProcessedEvent` markers are namespaced by generation id. A rebuild never reuses markers from another generation (this is a design direction agreed as the working basis; it is not a formal acceptance).

## 4. Protocol

1. **Preparation.** Create a candidate generation `g(n+1)` with an empty projection and its own dedupe namespace. The active generation `g(n)` is not touched.
2. **Cut point.** Define a consistent position `C` that separates history from live consumption. Options:
   - (a) a position per patient, using `pacienteSequence`;
   - (b) a global position in the history table.
   The choice depends on A4 and on how the history is ordered.
3. **Reconstruction.** Read the history up to `C` in `pacienteSequence` order per patient. Apply each event to `g(n+1)`, deduplicating by `eventId` inside `g(n+1)`.
4. **Catch-up.** Apply live events after `C` to `g(n+1)`. Two alternatives, to be decided:
   - (a) **Pause and resume:** pause the live consumer that writes to the active generation, replay from `C`, then resume. Simple, but stops updates during the window.
   - (b) **Dual write:** from `C` onward, write live events to both `g(n)` and `g(n+1)`. No pause, but the write path becomes more complex and must be tested.
5. **Validation.** Before activation, compare `g(n+1)` with a reference computed from the history: counts per level, average and p95 inputs, and per-patient state. Any mismatch blocks activation.
6. **Activation.** Change the active pointer from `g(n)` to `g(n+1)` in one atomic command (I5).
7. **Retirement.** Keep `g(n)` for the rollback window. Delete it after the window closes, under a defined policy.

## 5. Failure modes

| Failure | Expected behaviour | Required mechanism |
|---|---|---|
| Rebuild crashes midway | `g(n+1)` is partial and never active (I7) | Restart the rebuild from step 1, deleting the candidate generation first |
| Process interrupted during activation | Either the old or the new pointer is active, never neither (I1, I5) | Single atomic command; verify the pointer on restart |
| Redis down during rebuild | Rebuild stops; API-05 returns 503 per the API contract | Retry with backoff; no partial activation |
| Redis down after activation | Depends on persistence (A7) | Persistence decision required (Redis durability, gap identifier to be confirmed) |
| Consumer crashes after writing to `g(n+1)` but before committing the offset | Event is redelivered and ignored by `eventId` in `g(n+1)` (I2) | Dedupe per generation |
| Event in history but missing from Kafka, or the reverse | Mismatch detected in validation | Validation in step 5 blocks activation |

## 6. Acceptance tests (to be written before implementation)

- **Counts:** after rebuild and activation, counts equal the reference computed from the history.
- **Idempotency:** replaying a duplicate `eventId` within a generation does not change counts.
- **Cut point:** no event is lost or double-applied around `C`, for both catch-up alternatives.
- **Interruption:** killing the process at each step leaves the active generation unchanged, and a restart completes the rebuild.
- **Activation under load:** no reader sees a mixed state; 503 rate stays within the API contract.
- **Rollback:** after activation, restoring `g(n)` within the window restores the previous counts.
- **Version compatibility:** a history containing older envelope versions rebuilds correctly (depends on A3).

## 7. Open decisions

| Item | Decision needed | Blocks |
|---|---|---|
| CA-G-11 | TTL of `ProcessedEvent` relative to effective Kafka retention | Dedupe design |
| Redis durability (gap identifier to be confirmed) | Redis persistence (AOF or RDB) | Survival of generations across restarts |
| CA-G-24 | Completeness of rebuild against retention | Scope of the history |
| OD-3 | Open on `develop`. The 30-day retention is a documentary decision only on unmerged branch `feature/od-3-4-decisions`, and it is a working value only. Confirm the rebuild source (`EventoHistorico`) and the retention value | Step 3 |
| OD-5 | Retention and privacy of `EventoHistorico`; conflict with append-only | Production |
| G-03 | How `pacienteSequence` is assigned and enforced | I3 and A4 |
| DEC-03 | Envelope and versioning | A3 and version tests |
| RF-06 | Whether alerts belong to the reconstructible history | A6 |
| Step 4 | Catch-up alternative (a) pause or (b) dual write | Concurrency design |

## 8. What this document does not do

- It does not verify `EventoHistorico`, the outbox or Redis behaviour.
- It does not set TTL, retention or margin values.
- It does not change D0, D1, DEC-11, code, versions, ADRs or branches.
