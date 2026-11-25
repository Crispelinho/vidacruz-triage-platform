# ADR-10: Spring Boot Platform Line and Java Baseline

**Status:** Proposed  
**Date:** 2026-11-25  
**Decision:** Accept Spring Boot 3.5.16 (last open-source release of the 3.5.x line) on Java 21, for development and evaluation only, despite the end of open-source support on 2026-06-30.

---

## Context

The brief mandates Spring Boot 3.x (TC-01) for the Triage platform. Two skeleton projects were provided:
- `triage-service/triage-service/build.gradle.kts` — originally Boot 4.2.0
- `clinical-assistant-service/clinical-assistant-service/build.gradle.kts` — originally Boot 4.2.0

On `develop`, both build files already pin Spring Boot 3.5.16, Spring Cloud 2025.0.3 and Java 21 (toolchain), and the Clinical Assistant also pins Spring AI 1.1.1; both Gradle wrappers pin 8.14.8. This ADR records the reasoning behind those pins; it does not change them.

Additionally, the skeletons default to Java 25 (Gradle 9.x), but Spring Boot 3.5.x is documented to support Gradle 7–8, not 9.x.

**Official support status:**
- Spring Boot 3.5.x: open-source support ended 2026-06-30, about 5 months before this ADR's date (2026-11-25). Spring Boot 3.5.16 is the last open-source release of the 3.5.x line (official spring.io announcement of 2026-06-25, recorded in `requirements.md` AMB-12). Continued open-source support requires 4.0.x or 4.1.x.
- Spring Boot 4.1.x and later: described as supported in `requirements.md` AMB-12 (the support status of later lines is not independently verified here)
- Spring Cloud 2025.0.x: the release train paired with Boot 3.5.x (DEC-12). The pairing of Spring Cloud 2026.0.x with Boot 4.x is an assumption not verified in this repository; confirm on spring.io before any migration

**Timeline constraint:** The brief specifies a 3-day implementation window. Java 25 + Gradle 9.x introduces compatibility risk; Java 21 is an LTS release and aligns with Boot 3.5's tested toolchain (DEC-13).

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
| **Boot 3.5.x on Java 21** (recommended) | Fits brief; LTS Java; tested Gradle 8 support | No free security patches after 2026-06-30 (about 5 months ago); requires upgrade plan |
| **Boot 4.1.x on Java 21** | Active support; no EOL risk | Violates brief requirement for Boot 3.x |
| **Boot 3.5.x on Java 25 + Gradle 9.x** | Latest Java runtime | Untested combination; build failure risk in 3-day window; no support path if issues arise |
| **Wait for a later 3.x minor (3.6 or above)** | Would extend the support window | Not verified: `requirements.md` and the roadmaps document no later 3.x line, so this ADR does not assume one exists; the brief fixes the 3.x line |

---

## Decision

**Adopt Spring Boot 3.5.16 on Java 21 with Gradle 8.14.8 for development and evaluation.** Companion pins: Spring Cloud 2025.0.3 (both services) and Spring AI 1.1.1 GA (Clinical Assistant). These values match DEC-12 and DEC-13 in `requirements.md` and the C-01 baseline in `capabilities-map.md`. DEC-12 itself states the series (3.5.x, 2025.0.x, 1.1.x); the exact patch numbers come from `capabilities-map.md` C-01 and `implementation-roadmap.md` T-01, and must be re-checked on spring.io before the final build.

**Production Scope Clarification:**

Change 1 (platform-foundation) is **explicitly scoped as a 3-day prototype for evaluation**, not a production baseline. This ADR authorizes Spring Boot 3.5.x for:
- Development environments
- Evaluation and testing (3-day brief window)
- Non-production integration

If Change 1 were to become the production baseline after the brief, Boot 3.5.x would **violate this ADR** unless one of the production conditions listed below is met. Otherwise, post-delivery migration to Boot 4.1.x or later is required.

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
- Keep both skeletons at Boot 3.5.16 (already applied on `develop`; any change needs this ADR to be revisited).
- Keep the Gradle wrapper at 8.14.8 and the Java toolchain at 21.
- Document the end-of-support date (2026-06-30, passed), the scope restrictions and the production path options in the README and in this ADR.

---

## Verification

The following must pass before this ADR transitions from Proposed to Accepted:

- [ ] Gradle 8.14.8 build succeeds with Java 21 on both `triage-service` and `clinical-assistant-service` (the pins are present in the build files on `develop`; a passing build is not verified by this document)
- [ ] Spring Boot 3.5.16 and Spring Cloud 2025.0.3 confirmed at build time (via `./gradlew dependencies`), and the Boot 3.5.16 end-of-support statement re-checked on spring.io
- [ ] Spring AI 1.1.1 GA pgvector integration tested in Change 1 (platform-foundation)
- [ ] CI/CD gate: Java 21 toolchain version enforced in builds (not Java 25)
- [ ] README documents the scope restrictions (evaluation/development only) and post-delivery migration path (Boot 4.1.x required for production)

---

## Consequences

### Positive
- Meets brief constraint without additional risk.
- Aligns with Java 21, an LTS release.
- Clear toolchain for the team (Gradle 8 is stable).
- Passes immediate evaluation.

### Negative
- **No free security patches after 2026-06-30** (about 5 months ago as of 2026-11-25).
  - **Development/Evaluation scope:** This baseline is authorized for development, evaluation, and non-production integration only (3-day brief window).
  - **Production options:**
    1. **Commercial support:** Upgrade to VMware Spring Support contract for Boot 3.5.x security patches (eligibility requires contract terms).
    2. **Open-source support:** Migrate to Boot 4.1.x or later (requires upgrade planning and testing; post-delivery decision).
  - This ADR does NOT mandate Boot 4.1.x as the sole path; it permits either option depending on production requirements and support availability.

- Tight version pinning; no room for patch updates if a critical bug is found in Spring Cloud 2025.0.3.

- **Post-delivery migration required:** Spring Boot 3.5 open-source support ended **2026-06-30** (about 5 months before this ADR). If the platform moves beyond the 3-day evaluation window into production, Boot 4.1.x is required. Plan approximately 2–3 weeks for migration testing (dependency updates, API surface changes in Spring Boot 4.x, Spring Cloud 2026.0.x alignment, and pgvector Spring AI integration retesting).

---

## Risks

- **R-02 (Boot 3.5 end of open-source support):** Spring Boot 3.5 is past open-source support since 2026-06-30 (about 5 months before this ADR).
  - **Probability:** High (already occurred).
  - **Impact:** High (production use past June 2026 is unprotected).
  - **Mitigation:** Plan Boot 4.1 migration for post-delivery. Document this decision in the README and in the architecture decision record.

- **R-05 (Spring AI 1.x drift):** Spring AI 1.1.x GA must be verified on spring.io at build time.
  - **Probability:** Low (1.1.x is GA, not a milestone).
  - **Impact:** Medium (mismatched version breaks pgvector starter).
  - **Mitigation:** Pin exact version; test pgvector integration in change 1 (platform-foundation).

---

## References

- Brief: TC-01 (Spring Boot 3.x required, 3.5.x line)
- Decision register: DEC-12 (version pins), DEC-13 (Java 21 target), AMB-12 (Boot 3.5 open-source support)
- Baseline: `capabilities-map.md` C-01, `implementation-roadmap.md` T-01 and G-02, `openspec-roadmap.md` change 1
- Risk register: R-02 (Boot 3.5 end of open-source support), R-05 (Spring AI 1.x version drift)
- OpenSpec change 1: `platform-foundation` (builds and containers)
- Required before: change 1 (all changes depend on the platform)

---

## Linked Open Decision

**OD-5** (retention period of `EventoHistorico`): Not affected by this ADR. It is open and must be set before production use (`capabilities-map.md`).
