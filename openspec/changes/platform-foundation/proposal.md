# Proposal: platform-foundation (change 1, capability C-01)

## Intent

Both services (Triage and Clinical Assistant) build, run and start in containers on the pinned baseline, with the test infrastructure needed by every later change. This change is the first in `docs/openspec-roadmap.md` §3 and owns capability C-01 of `docs/capabilities-map.md`.

## Scope

In scope:
- Baseline: Spring Boot 3.5.16, Java 21, Gradle 8.14.8 with a verified wrapper, Spring Cloud 2025.0.3, Spring AI 1.1.1 (assistant only).
- Package naming and build hygiene of both skeletons.
- Testcontainers for PostgreSQL (pgvector in the assistant), Kafka and Redis.
- Flyway baseline and the append-only audit table definition (DER-04), without its writers.
- Startup configuration validation (DER-01) when the properties exist.

Out of scope (owned by later changes):
- Business behavior, endpoints, events, outbox writers (changes 2 to 11).
- Kubernetes and Azure manifests and the Dockerfile delivery path (change 12, `platform-delivery`). The scaffold audit lists a Dockerfile finding; it is tracked as a task of change 12 unless the developer decides otherwise.

## Gates (must be met before this change starts)

Per `docs/implementation-roadmap.md` §2.1 and `docs/adr-roadmap.md` §3.3, these ADRs must be **Accepted** before change 1 starts: ADR-01 (decomposition), ADR-10 (Boot 3.5.16 and Java 21, with the accepted end-of-OSS-support risk), ADR-14 (one PostgreSQL per service).

Status at the time of writing: **none of the three is accepted.** Change 1 is therefore recorded as in progress on a provisional basis. Its tasks may be implemented and tested, but the change is not closed until the gates are met.

## Evidence rules and legacy scaffold

- Each task records its Red evidence (failing test) before its Green evidence.
- **Legacy scaffold work:** Existing work from the scaffold audit (commits before this change opened) is classified as "Green only, Red not recorded" and is **not counted as TDD evidence**. It may be reutilized as a valid starting point for this change, but any modification to it must be covered by RED tests before the Green implementation.
- Only TDD evidence (RED before GREEN) is recognized as proof of proper methodology. Retrospective claims of TDD compliance are not accepted.
- Every change lists the test command, the result, and any test that could not run.

## Affected requirements

NFR-12, NFR-13 (delivery only; manifests in change 12), NFR-14 (change 12), NFR-15, NFR-17, TC-01, TC-02, TC-03, TC-09, TC-10, TC-11, TC-12, DER-01, DER-04, AMB-12, AMB-13 (see `docs/traceability-matrix.md`).
