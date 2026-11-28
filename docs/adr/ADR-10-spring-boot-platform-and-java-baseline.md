# ADR-10: Spring Boot Platform Line and Java Baseline

**Status:** Accepted — developer-approved baseline for evaluation scope (2026-11-27)  
**Date:** 2026-11-25  
**Decision:** Accept Spring Boot 3.5.x on Java 21 despite EOL risk in June 2026.

---

## Context

The brief mandates Spring Boot 3.x (TC-01) for the Triage platform. Two skeleton projects were provided:
- `triage-service/.../build.gradle.kts:3` — originally Boot 4.2.0
- `clinical-assistant-service/.../build.gradle.kts:3` — originally Boot 4.2.0

Additionally, the skeletons default to Java 25 (Gradle 9.x), but Spring Boot 3.5.x is documented to support Gradle 7–8, not 9.x.

**Official support status:**
- Spring Boot 3.5.x: open-source support ended 2026-06-30 (approximately 5 months before this decision date of 2026-11-25)
- Spring Boot 4.1.x and later: active support
- Spring Cloud 2025.0.x (Northfields): built for Boot 3.5.x; 2026.0.x is for Boot 4.x only

**Timeline constraint:** The brief specifies a 3-day implementation window. Java 25 + Gradle 9.x introduces compatibility risk; Java 21 is LTS (long-term support until September 2031) and aligns with Boot 3.5's tested toolchain.

---

## Problem

1. **Version mismatch:** Skeletons target Boot 4.2.0, which is outside the brief scope.
2. **Java-Gradle incompatibility:** Boot 3.5 + Java 25 + Gradle 9.x is not a documented combination.
3. **EOL risk:** Boot 3.5.x is past open-source support; continued security patches require paid VMware support or an upgrade to Boot 4.x.
4. **Brief constraint:** Boot 4.x is not an option per the brief.

The choice is not whether to use Boot 3.5, but whether to accept its EOL status and plan an upgrade path.

---

## Alternatives Considered

| Alternative | Pros | Cons |
|---|---|---|
| **Boot 3.5.x on Java 21** (recommended) | Fits brief; LTS Java; tested Gradle 8 support | No free security patches after 2026-06-30 (already passed); requires upgrade plan |
| **Boot 4.1.x on Java 21** | Active support; no EOL risk | Violates brief requirement for Boot 3.x |
| **Boot 3.5.x on Java 25 + Gradle 9.x** | Latest Java runtime | Untested combination; build failure risk in 3-day window; no support path if issues arise |
| **Wait for Boot 3.6 or later 3.x** | Extends support window | No 3.6 release planned; brief specifies 3.x with no flexibility on that line |

---

## Decision

**Adopt Spring Boot 3.5.x (latest patch, verify on spring.io) on Java 21 with Gradle 8.x for development and evaluation.**

**Production Scope Clarification:**

Change 1 (platform-foundation) is **explicitly scoped as a 3-day prototype for evaluation**, not a production baseline. This ADR authorizes Spring Boot 3.5.x for:
- Development environments
- Evaluation and testing (3-day brief window)
- Non-production integration

If Change 1 were to become production baseline after the brief, Boot 3.5.x would **violate this ADR** (no commercial support available). Post-delivery migration to Boot 4.1.x would be required.

**Scope:** This decision applies to:
- Development environments
- Evaluation and testing (3-day brief window)
- Non-production integration

**NOT authorized for production without meeting one of these conditions:**
1. VMware Spring Support contract with current security patch commitment for Boot 3.5.x, OR
2. Migration to Boot 4.1.x or later (open-source support; planned post-delivery)

Rationale:
1. Meets brief requirement (Boot 3.x) for evaluation window.
2. Java 21 is LTS; Gradle 8 is a proven pair with Boot 3.5.
3. Avoids build-time risk in a 3-day sprint.
4. Allows immediate implementation; production path is a post-delivery decision based on support availability.

**Actions:**
- Update both skeletons: `build.gradle.kts` to Boot 3.5.x latest patch.
- Set Gradle wrapper to 8.14.8 (or latest 8.x).
- Set Java toolchain version to 21.
- Document the EOL date (2026-06-30 passed), scope restrictions, and production path options in README and ADR.

---

## Support-risk acceptance (D-ADR-10 / R-02, 2026-11-27)

The Developer/User accepts in writing the end of open-source support for Spring Boot 3.5.16. The official Spring announcement of 2026-06-25 (https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/) records 3.5.16 as the last OSS release of the 3.5.x line; continued OSS support requires 4.0.x or 4.1.x. The brief requires Boot 3.x, so the supported 4.x line is excluded for this evaluation. This closes the R-02 decision only; it does not mark the ADR Accepted, because the verification gates below remain open.

## Verification

The following gates support acceptance of this ADR:

- [x] Gradle 8.14.8 wrapper is configured in both services (`triage-service/triage-service/gradle/wrapper/gradle-wrapper.properties`, `clinical-assistant-service/clinical-assistant-service/gradle/wrapper/gradle-wrapper.properties`) and Java 21 toolchain is enforced in both `build.gradle.kts` files.
- [x] Spring Boot 3.5.16 is pinned in both services; Spring Cloud 2025.0.3 and Spring AI 1.1.1 are pinned in `clinical-assistant-service` where required. CI builds both services with Java 21 in `.github/workflows/triage-service.yml` and `.github/workflows/clinical-assistant-service.yml`; the duplicate `ci.yml` was removed under M-3.
- [x] pgvector/Spring AI integration is covered by tests in `clinical-assistant-service` (contract/integration/db tests under `src/test/java/com/vidacruz/clinical/...pgvector...`, `CorpusPgvectorTest`, `VectorRepositoryTest`, etc.).
- [x] CI/CD gate enforces Java 21 (`actions/setup-java@v4`, `java-version: '21'`) in CI workflows.
- [x] README documents Boot 3.5.16/Java 21 baseline and EOL/migration note (`README.md`).
- [x] Reproducible build evidence: on `develop`, [triage-service run 46143013981](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46143013981) (head `f4e17ab`, 2026-11-28T12:06:22Z) and [clinical-assistant-service run 46153420758](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46153420758) (head `b8223b8`, 2026-11-28T14:12:02Z) completed `success`, each running `./gradlew build` in its service directory. The earlier aggregate `CI` run [46056455183](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46056455183) (head `93706912fc1d21e3ef8a72e8b84faa5310acbb3a`, 2026-11-27T19:24:16Z) is historical evidence from before `ci.yml` was removed and is not a current required check.
- [x] No production deployment claim on OSS-only Boot 3.5.x found in README/docs review; README states "Production note: Boot 3.5.16 reached EOL..." and requires commercial support or Boot 4.1+ migration. ADR-10 records R-02 acceptance and post-delivery Boot 4.1+ path. Historical ADR-06 "Production-ready" wording refers to webhook receiver replaceability, not the Boot 3.5 OSS baseline.

Formal acceptance recorded by developer approval in conversation on 2026-11-27; this ADR is accepted for the evaluation scope documented above, not as a production OSS support claim.

---

## Consequences

### Positive
- Meets brief constraint without additional risk.
- Aligns with LTS Java 21 (support until 2031).
- Clear toolchain for the team (Gradle 8 is stable).
- Passes immediate evaluation.

### Negative
- **No free security patches after 2026-06-30** (already past).
  - **Development/Evaluation scope:** This baseline is authorized for development, evaluation, and non-production integration only (3-day brief window).
  - **Production options:**
    1. **Commercial support:** Upgrade to VMware Spring Support contract for Boot 3.5.x security patches (eligibility requires contract terms).
    2. **Open-source support:** Migrate to Boot 4.1.x or later (requires upgrade planning and testing; post-delivery decision).
  - This ADR does NOT mandate Boot 4.1.x as the sole path; it permits either option depending on production requirements and support availability.

- Tight version pinning; no room for patch updates if a critical bug is found in Spring Cloud 2025.0.3.

- **Post-delivery migration required:** Spring Boot 3.5 open-source support ended **2026-06-30** (already passed). If the platform moves beyond the 3-day evaluation window into production, Boot 4.1.x is required. Plan approximately 2–3 weeks for migration testing (dependency updates, API surface changes in Spring Boot 4.x, Spring Cloud 2026.0.x alignment, and pgvector Spring AI integration retesting).

---

## Risks

- **R-02 (EOL risk):** Spring Boot 3.5 is past open-source support as of 2026-06-30.
  - **Probability:** High (already occurred).
  - **Impact:** High (production use past June 2026 is unprotected).
  - **Mitigation:** Plan Boot 4.1 migration for post-delivery. Document this decision in the README and in the architecture decision record.

- **R-05 (Spring AI 1.x drift):** Spring AI 1.1.x GA must be verified on spring.io at build time.
  - **Probability:** Low (1.1.x is GA, not a milestone).
  - **Impact:** Medium (mismatched version breaks pgvector starter).
  - **Mitigation:** Pin exact version; test pgvector integration in change 1 (platform-foundation).

---

## References

- Brief: TC-01 (Spring Boot 3.x required)
- Decision register: DEC-12 (version pins), DEC-13 (Java 21 target)
- Risk register: R-02 (Boot 3.5 EOL), R-05 (Spring AI drift)
- OpenSpec change 1: `platform-foundation` (builds and containers)
- Required before: change 1 (all changes depend on the platform)

---

## Linked Open Decision

**OD-5** (retention period of `EventoHistorico`): Not affected by this ADR. Set post-delivery.
