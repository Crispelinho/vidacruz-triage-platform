# Tasks: platform-foundation

Status legend: `done` (evidence recorded, reviewed), `done-unreviewed` (commit exists on a branch, not reviewed), `pending`, `blocked`.
Red/Green column: `R+G` = failing test recorded before the fix; `G` = green only (no Red recorded); `—` = not applicable.

## Gates

- [ ] G-01 ADR-01 accepted — `blocked` (not accepted)
- [ ] G-02 ADR-10 accepted, with the Boot 3.5 end-of-OSS-support risk recorded — `blocked`
- [ ] G-03 ADR-14 accepted — `blocked`

## Baseline and build

| ID | Task | Status | Red/Green | Evidence |
|---|---|---|---|---|
| 1.1 | Triage skeleton with Gradle wrapper 8.14.8 | done-unreviewed | G | `9040224` on `feature/scaffold-skeletons` |
| 1.2 | Assistant skeleton with Gradle wrapper 8.14.8 | done-unreviewed | G | `4d59348` on `feature/scaffold-skeletons` |
| 1.3 | Boot 3.5.16, Spring Cloud 2025.0.3, Spring AI 1.1.1 in both builds; Boot 4 starters removed | done-unreviewed | G | `fix/scaffold-audit-fixes`, `b2f328a` |
| 1.4 | Java 21 toolchain in both builds | pending | — | verify `./gradlew build` on JDK 21 |
| 1.5 | Wrapper distribution SHA-256 pinned in both services | done-unreviewed | G | `3ddaf4c` |
| 1.6 | `gradlew` executable (mode 100755) in both services | done-unreviewed | G | `fb6fc39` |

## Packaging and repository hygiene

| ID | Task | Status | Red/Green | Evidence |
|---|---|---|---|---|
| 2.1 | Gitignore rules organized by type | done-unreviewed | — | `8f490a1` |
| 2.2 | Secret env files ignored, wrapper jar tracked | done-unreviewed | G | `f5a1105` |
| 2.3 | Lowercase package names without underscores | done-unreviewed | G | `4817d27`; tests pass in an ASCII copy |
| 2.4 | Commit authorship: every commit on this change carries `Crispelinho <cristhian.carpio18@gmail.com>` | pending | — | `b2f328a`, `fb6fc39`, `f5a1105` carry another author; rewrite only after the developer approves |

## Test infrastructure

| ID | Task | Status | Red/Green | Evidence |
|---|---|---|---|---|
| 3.1 | Triage context test with Testcontainers (PostgreSQL, Kafka) | done-unreviewed | G | `b2f328a`; 1/1 in an ASCII copy |
| 3.2 | Assistant context test with Testcontainers (pgvector, Kafka, Redis) | pending | — | fails on JDBC DataSource; explicit JDBC wiring in `641920c` is not yet verified |
| 3.3 | Record a Red run for 3.1 and 3.2 (failing before the fix) | pending | R+G | required by the evidence rule |

## Persistence and configuration

| ID | Task | Status | Red/Green | Evidence |
|---|---|---|---|---|
| 4.1 | Flyway baseline in Triage | pending | — | depends on G-03 |
| 4.2 | Append-only audit table: UPDATE and DELETE rejected (test) | pending | — | depends on G-03 and 4.1 |
| 4.3 | Startup configuration validation (DER-01) | pending | — | depends on the configuration design |
| 4.4 | Explicit JDBC DataSource for pgvector, R2DBC for requests | pending | — | depends on 3.2 |

## Out of scope for this change (moved)

- Dockerfile review and correction: moved to change 12 (`platform-delivery`), unless the developer decides to keep it here.
- Low-severity audit findings: each one is assigned to the change it belongs to (packaging, delivery or observability) before it is resolved.

## Done criteria

1. Every task in the tables above is `done` with its evidence, or explicitly moved.
2. Every gate is `Accepted` before this change is closed.
3. `docs/traceability-matrix.md` rows for the listed requirements point to these task IDs.
4. Red evidence is recorded for every task that changes behavior; tasks without it are marked `G`.
