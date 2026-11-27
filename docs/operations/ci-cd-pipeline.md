# VidaCruz — CI/CD Pipeline Proposal

Status: **proposal, not implemented.** No `.github/workflows/` file exists in this change. Requirements: `prompt-init.txt` §12.4 and NFR-12, NFR-13, NFR-14, NFR-17, NFR-18. Baseline versions: Java 21 (DEC-13, ADR-10), Spring Boot 3.5.16, Gradle 8.14.8, Spring Cloud 2025.0.3, Spring AI 1.1.1 for the Assistant.

## 1. Goals

- Keep `develop` green: every pull request runs the full gate set before merge.
- Make each gate reproducible locally with one Gradle or Docker command.
- Fail on the rules that protect clinical logic (classification, SLA, idempotency, audit), not only on style.

## 2. Pipeline stages

Each service builds independently. Shared contracts (`contracts/events`, `contracts/openapi`) are validated first because both services depend on them.

| Stage | Runs on | Tools | Blocking | Purpose |
|---|---|---|---|---|
| 1. Format | PR | Spotless (google-java-format) | Yes | Formatting check, no auto-fix in CI |
| 2. Compile | PR | `./gradlew compileJava compileTestJava` on JDK 21 | Yes | Toolchain enforced (ADR-10 gate) |
| 3. Unit tests | PR | JUnit 5, `./gradlew test` | Yes | Classification boundaries E1–E6, SLA, transitions, validations |
| 4. Static analysis | PR | SpotBugs and Checkstyle, or SonarQube if the organisation provides it | Yes for new violations | Null handling, resource leaks, reactive misuse |
| 5. Contract checks | PR | OpenAPI lint (Spectral) and event schema check against `contracts/events` | Yes | API-01…API-06 and EVT-01…EVT-04 stay compatible |
| 6. Integration tests | PR and `develop` | Testcontainers: PostgreSQL 17, Kafka, Redis | Yes on `develop`; on PR only the fast subset | Outbox, idempotent consumers, transactional behaviour, pgvector |
| 7. Architecture rules | PR | ArchUnit | Yes | No JDBC on the reactive path (NFR-01), no synchronous call from Triage to Assistant (NFR-04) |
| 8. Image build | `develop` and tags | Docker Buildx, multi-stage Dockerfile | Yes | Image builds with non-root user |
| 9. Vulnerability scan | `develop` and tags | Trivy on the image and on dependencies (`trivy fs`) | Yes for CRITICAL and HIGH with a fix available | Scan for known CVEs |
| 10. Compose smoke test | `develop` | `docker compose up` with health checks | Yes | NFR-12: one command starts everything |
| 11. Deploy to AKS or Container Apps | tags only | Helm or Kustomize overlays from `infrastructure/kubernetes/overlays/` | Manual approval | NFR-13, NFR-14 |

## 3. Gate decisions

| Gate | Decision | Reason |
|---|---|---|
| Scanner severity | Block on CRITICAL and HIGH that have a fixed version; report the rest | Avoids blocking on unfixable findings; stays proportionate (§12.6) |
| Integration tests on every PR | Subset only; full set on `develop` | Testcontainers is slow; keeps PR feedback under about 10 minutes (assumption, to be measured) |
| Secrets | No secret in the repository; `gitleaks` on PR | NFR-09 |
| Version pinning | Gradle wrapper checksum verified; base images pinned by digest | Reproducible builds |
| Coverage | Report only in Day 1; no hard threshold until the baseline is measured | Avoids a threshold that encourages empty tests |

## 4. Required secrets and variables

| Name | Type | Used by |
|---|---|---|
| `LLM_API_KEY` | Secret | Assistant tests use a mock; the real key is used only in the deploy stage |
| `ACR_*` or GHCR credentials | Secret | Image push |
| `AKS_*` or Container Apps identifiers | Variable | Deploy stage |

No test in the pipeline calls the real LLM provider (NFR-15: LLM mockable).

## 5. Traceability in the pipeline

- Each test class names the requirement IDs it covers in its Javadoc, for example `RN-01 / TST-CLS-01…06`.
- A report step compares those IDs against `docs/traceability-matrix.md` and fails when a mandatory requirement has no test.
- This is the mechanism for §12.5 (continuous traceability). It is a proposal until the first version of the matrix is populated.

## 6. Open items

| ID | Item | Owner |
|---|---|---|
| CI-01 | Confirm whether the organisation provides SonarQube | Developer |
| CI-02 | Choose the image registry (GHCR or Azure Container Registry) | Developer and ADR-11 |
| CI-03 | Decide the PR integration test subset | Developer, after measuring the first run |
| CI-04 | Confirm Docker Buildx and Trivy are available on the runner | Developer |
