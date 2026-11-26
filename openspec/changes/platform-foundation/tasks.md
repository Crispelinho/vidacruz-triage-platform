# Tasks: platform-foundation

Status legend: `done` (evidence recorded, reviewed), `done-unreviewed` (commit exists on a branch, not reviewed), `legacy-green-only-not-counted` (preexisting scaffold work, Green only, no Red recorded, not counted as TDD evidence), `pending`, `blocked`.
Red/Green column: `R+G` = failing test recorded before the fix; `G` = green only (no Red recorded); `—` = not applicable.

## Gates

- [ ] G-01 ADR-01 accepted — `blocked` (not accepted)
- [ ] G-02 ADR-10 accepted, with the Boot 3.5 end-of-OSS-support risk recorded — `blocked`
- [ ] G-03 ADR-14 accepted — `blocked`

## Baseline and build

| ID | Task | Status | Red/Green | Evidence |
|---|---|---|---|---|
| 1.1 | Triage skeleton with Gradle wrapper 8.14.8 | legacy-green-only-not-counted | G | `9040224` on `feature/scaffold-skeletons` (preexisting scaffold) |
| 1.2 | Assistant skeleton with Gradle wrapper 8.14.8 | legacy-green-only-not-counted | G | `4d59348` on `feature/scaffold-skeletons` (preexisting scaffold) |
| 1.3 | Boot 3.5.16, Spring Cloud 2025.0.3, Spring AI 1.1.1 in both builds; Boot 4 starters removed | legacy-green-only-not-counted | G | `fix/scaffold-audit-fixes`, `b2f328a` (preexisting scaffold audit) |
| 1.4 | Java 21 toolchain in both builds | pending | R+G | RED: test fails without JDK 21; GREEN: `./gradlew build` passes on JDK 21 in both services |
| 1.5 | Wrapper distribution SHA-256 pinned in both services | legacy-green-only-not-counted | G | `3ddaf4c` (preexisting scaffold audit) |
| 1.6 | `gradlew` executable (mode 100755) in both services | legacy-green-only-not-counted | G | `fb6fc39` (preexisting scaffold audit) |

## Packaging and repository hygiene

| ID | Task | Status | Red/Green | Evidence |
|---|---|---|---|---|
| 2.1 | Gitignore rules organized by type | legacy-green-only-not-counted | — | `8f490a1` (preexisting scaffold) |
| 2.2 | Secret env files ignored, wrapper jar tracked | legacy-green-only-not-counted | G | `f5a1105` (preexisting scaffold audit) |
| 2.3 | Lowercase package names without underscores | legacy-green-only-not-counted | G | `4817d27`; tests pass in an ASCII copy (preexisting scaffold) |
| 2.4 | Commit authorship: every commit on this change carries `Crispelinho <cristhian.carpio18@gmail.com>` | pending | — | `b2f328a`, `f5a1105`, `fb6fc39` carry "Orchestrator" author; rewrite decision pending developer review (impact analysis needed) |

## Test infrastructure

| ID | Task | Status | Red/Green | Evidence |
|---|---|---|---|---|
| 3.1 | Triage context test with Testcontainers (PostgreSQL, Kafka) | legacy-green-only-not-counted | G | `b2f328a` (preexisting scaffold); 1/1 in an ASCII copy. RED not recorded; will record RED run if implementation changes. |
| 3.2 | Assistant context test with Testcontainers (pgvector, Kafka, Redis) | pending | R+G | RED: test fails (JDBC DataSource not wired); GREEN: explicit JDBC wiring verified in test |
| 3.3 | Record a Red run for 3.1 (legacy scaffold baseline) | pending | — | A single RED run baseline for 3.1 before any modifications to the test, to establish preexisting behavior |

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

1. Every task in the tables above is `done` with its evidence, or explicitly moved, or classified as `legacy-green-only-not-counted`.
2. Every gate is `Accepted` before this change is closed.
3. `docs/traceability-matrix.md` rows for the listed requirements point to these task IDs.
4. Red evidence is recorded for every task that changes behavior. Legacy scaffold tasks are marked `legacy-green-only-not-counted` and are not counted as TDD evidence. If modified, they must be covered by RED tests before GREEN implementation.
