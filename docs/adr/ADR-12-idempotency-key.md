# ADR-12: Idempotency-Key replay for registration

- **Status:** Proposed. Not accepted. DEC-15 is "Accepted (user)" for the requirement scope and the roadmap calls this ADR "Proposed (accepted as scope by the user)", but this ADR itself is not accepted and its scope, key-format and cleanup values remain pending.
- **Date:** 2026-11-25
- **Deciders:** developer (pending)
- **Scope:** C-02 replay semantics of `POST /triage/pacientes` (API-01) and the Triage `IdempotencyKey` store. It excludes the classification and SLA rules of RF-01, the outbox and event-envelope contract (ADR-03, ADR-02), and authentication (ADR-07).
- **Related:** C-02, RF-01, DEC-15, R-09, API-01, TST-IDM, G-T03, G-T04, G-T05, ADR-03, ADR-07, ADR-02, G-08, T-05, T-07 in [requirements.md](../requirements.md), [capabilities-map.md](../capabilities-map.md), [triage-service.md](../services/triage-service.md), [implementation-roadmap.md](../implementation-roadmap.md), [openspec-roadmap.md](../openspec-roadmap.md) and [adr-roadmap.md](../adr-roadmap.md)

## Context

R-09 (requirements.md:489) records duplicate patients on client retry because the brief defines no key. DEC-15 (requirements.md:449) is "Accepted (user, 2026-11-24)" for the scope: an optional `Idempotency-Key` header on `POST /triage/pacientes`, stored with a request hash, the patient id and the original response in `IdempotencyKey` (24 h TTL), with the original response on same key and same body and 409 on the same key with a different body; the requirement must be implemented and tested, and ships after mandatory requirements if the deadline is at risk. The roadmap (adr-roadmap.md:51) marks ADR-12 "Proposed (accepted as scope by the user)", which is a statement about scope, not acceptance of this ADR.

RF-01 (requirements.md:46) and API-01 (requirements.md:227, :270-272) define the observable contract: replay returns the original 201 with no second patient, and a reused key with a different body returns 409 `IDEMPOTENCY_KEY_REUSED`. C-02 owns the endpoint and the key store (capabilities-map.md:19, :77-83). The `IdempotencyKey` table is specified in triage-service.md:267-279: `key`, `request_hash`, `paciente_id`, `response_status`, `response_body`, `created_at` and `expires_at = created_at + 24 h`, written in the same transaction as the patient insert. A primary-key conflict on `key` serializes concurrent same-key requests (triage-service.md:325-331).

Open gaps remain: G-T03 (global versus per-client scope, in-flight duplicates, key format and length), G-T04 (empty or malformed header) and G-T05 (cleanup job for expired rows) in triage-service.md:495-497. The replay key is not the event `eventId` of DEC-03/AMB-14; it identifies a client request, while `eventId` identifies a published event. Replay correctness also depends on the ADR-03 transaction, because the key row and EVT-01 outbox row must commit with the patient.

## Decision drivers

- R-09: a retry must not create a second patient or a second EVT-01.
- Same key and same canonical body return the original 201 response; same key and different body return 409 `IDEMPOTENCY_KEY_REUSED` (API-01).
- Concurrent same-key requests create exactly one patient and one EVT-01 (TST-IDM, triage-service.md:426).
- The key row is written in the same transaction as the patient and the outbox row (ADR-03).
- A request that fails validation never stores a key; a request without the header behaves as today (one new patient per POST).
- Expired rows are removed after the 24 h TTL (DEC-15), with the cleanup schedule pending.
- Pending values are marked explicitly; no unresolved scope, format, length or schedule is invented here.

## Alternatives considered

| Option | Description | Pros | Cons | Status / why not chosen |
|---|---|---|---|---|
| **A. Optional key with stored replay in the registration transaction** | Accept `Idempotency-Key` on `POST /triage/pacientes`; store the canonical-body SHA-256 hash, patient id, original response and 24 h expiry in `IdempotencyKey`, inserted before the patient in the same transaction | Meets RF-01/API-01 and TST-IDM; PK conflict gives concurrency control; no header means unchanged behavior | Requires a new table and cleanup; scope, key format/length and cleanup are still open (G-T03/04/05); depends on ADR-03 | **Proposed**, not accepted. Pending developer acceptance and closure of G-T03/04/05 |
| **B. No idempotency** | Do not implement the header; each POST creates a patient | No new store, no replay logic; ships first | Leaves R-09 unmitigated and contradicts DEC-15's accepted scope and TST-IDM; duplicate retries create duplicate patients and EVT-01 rows | **Rejected**: this is the only alternative recorded in DEC-15's register; it is the current brief scope, not an accepted design |
| **C. Global-key-only scope** | Treat `key` as globally unique without client identity | Simplest primary key; one lookup per replay | Cannot distinguish clients that choose the same key, so one client's value can block another (G-T03, triage-service.md:495); in-flight behavior is still open | **Rejected / deferred**: no second DEC alternative exists; this comes from the G-T03 scope gap and can be revisited after the ADR is accepted |

## Decision

**Proposed: Option A.** `POST /triage/pacientes` accepts an optional `Idempotency-Key` header. The canonical request body (sorted keys, normalized values after validation) is hashed with SHA-256 and the hash, the resulting patient id, the original 201 response and `expires_at = created_at + 24 h` are stored in `IdempotencyKey` in the same transaction as the patient insert and the EVT-01 outbox row. Same key and same hash return the original 201 body with no new patient and no second event; the same key with a different hash returns 409 `IDEMPOTENCY_KEY_REUSED` (API-01); a failed validation request stores no key; without the header each POST creates a patient. Concurrent same-key requests wait on the primary-key conflict and produce exactly one patient and one EVT-01. Pending, not decided here: global versus `(client identity, key)` scope, key format and maximum length, and the cleanup schedule for expired rows (G-T03, G-T04, G-T05). The service analysis proposes `(client identity, key)` scope, `varchar(255)` and treating an empty header as malformed, but those proposals are not adopted by this ADR. The replay key is distinct from the event `eventId`. This is a proposal, not an accepted decision.

## Consequences

- Positive: R-09 is addressed without changing the no-key behavior of RF-01/API-01; replays are observable through the stored original response.
- Negative: the Triage schema gains a key store that needs cleanup, and replay depends on the ADR-03 atomic transaction.
- C-02 owns the endpoint, the key store and the replay tests; EVT-01 emission stays with the outbox path (ADR-03, ADR-02).
- API-01 keeps 201 on replay and 409 `IDEMPOTENCY_KEY_REUSED`; TST-IDM becomes the acceptance evidence.
- The replay key is separate from `eventId`; consumer dedupe in C-05 still uses `pacienteSequence` and `eventId`.
- G-08 and G-07 (ADR-07) gate T-05; an unmet gate blocks change 3 (implementation-roadmap.md:39).

## Risks and mitigations

- **R-09 (duplicate patients on retry):** store the hash and response with a primary key on `key`; the conflict path replays or rejects. Residual risk remains until TST-IDM passes.
- **G-T03 (scope, in-flight, format, length):** global-key-only scope is rejected because it cannot distinguish clients; the chosen scope, format and length are pending and must be closed before implementation.
- **G-T04 (empty header):** treatment of an empty value is pending; do not assume absent or malformed behavior here.
- **G-T05 (cleanup):** expired rows grow the table; the scheduled deletion policy is pending and not invented here.
- **ADR-03 dependency:** if the patient, key row and outbox row are not atomic, a replay can disagree with EVT-01; replay waits for that decision.
- **DEC-15 deferral:** if the deadline is at risk, the requirement stays a documented proposal; that deferral must be explicit, not silent.

## Verification

- **TST-IDM (requirements.md:398, triage-service.md:422-428):** same key and body → 201 with the original body, `COUNT(paciente)` unchanged and no second EVT-01; same key, different body → 409 `IDEMPOTENCY_KEY_REUSED`; no key → a new patient on each POST; concurrent same-key requests → exactly one patient and one EVT-01; failed validation with a key → 400 with no key stored; expiry after 24 h in a time-controlled test, with expired rows removed by the cleanup job.
- **T-07 (implementation-roadmap.md:51):** implement the 24 h replay and the 409 on a reused key with a different body; the task's verifiable result is "Replay returns the original response; a different body returns 409".
- **Change 3 done criterion (openspec-roadmap.md:28):** a repeated `Idempotency-Key` returns the original response and a reused key with a different body returns 409, alongside the registration transaction test.
- **Gates (implementation-roadmap.md:32-33, :39):** G-08 (this ADR) and G-07 (ADR-07) must be accepted before T-05; ADR-03 must be accepted before the transaction can be relied on. An unmet gate blocks change 3.
