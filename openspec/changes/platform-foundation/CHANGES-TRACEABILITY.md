# Platform Foundation — 6 Cambios: Diff + Matriz de Trazabilidad

**Commit:** `32928a6` (docs(review): resolve 6 critical blockers)  
**Date:** 2026-11-25  
**Status:** ✅ Cambios aplicados y commiteados (documentación local solamente)

---

## Resumen de Cambios

| # | Archivo | Líneas | Tipo | Autorización |
|---|---|---|---|---|
| **1** | `docs/adr/ADR-10-spring-boot-platform-and-java-baseline.md` | Decision (l48–62), Consequences (l75–80) | Restricción production flexible | ✅ Usuario |
| **2** | `openspec/changes/platform-foundation/proposal.md` | Done Criteria (l60–80), Motivation (l84–94) | Verificabilidad + dependencias ADRs | ✅ Usuario |
| **3** | `openspec/changes/platform-foundation/tasks.md` | T6.4 removed, T7.4 added | Scope correction + verification task | ✅ Usuario |
| **4** | `openspec/changes/platform-foundation/spec.md` | Production restriction (top) | Warning box + desbloqueo conditions | ✅ Usuario |
| **5** | `openspec/changes/platform-foundation/REVIEW-BLOCKERS.md` | NEW | Auditoría crítica (referencia) | ✅ Usuario |

---

## CAMBIO 1: ADR-10 — Restricción Production Flexible

**Archivo:** `docs/adr/ADR-10-spring-boot-platform-and-java-baseline.md`

### Antes (Decision section)
```
Decision: Adopt Spring Boot 3.5.x on Java 21 with Gradle 8.x
Rationale:
1. Meets brief requirement (Boot 3.x).
2. Java 21 is LTS; Gradle 8 is a proven pair with Boot 3.5.
3. Avoids build-time risk in a 3-day sprint.
4. Allows immediate implementation; upgrade path is a post-delivery decision.

Actions:
- Update both skeletons: build.gradle.kts to Boot 3.5.x latest patch.
```

### Después (Decision section)
```
Decision: Adopt Spring Boot 3.5.x (latest patch, verify on spring.io) on Java 21 with Gradle 8.x 
FOR DEVELOPMENT AND EVALUATION.

Scope: This decision applies to:
- Development environments
- Evaluation and testing (3-day brief window)
- Non-production integration

NOT authorized for production without meeting one of these conditions:
1. VMware Spring Support contract with current security patch commitment for Boot 3.5.x, OR
2. Migration to Boot 4.1.x or later (open-source support; planned post-delivery)

Rationale:
1. Meets brief requirement (Boot 3.x) for evaluation window.
2. Java 21 is LTS; Gradle 8 is a proven pair with Boot 3.5.
3. Avoids build-time risk in a 3-day sprint.
4. Allows immediate implementation; production path is a post-delivery decision based on support availability.

Actions:
- Update both skeletons: build.gradle.kts to Boot 3.5.x latest patch.
- Document the EOL date (2026-06-30 passed), scope restrictions, and production path options in README and ADR.
```

### Antes (Consequences/Negative)
```
- **No free security patches after 2026-06-30** (already past).
  - **Mitigation:** Treat the upgrade to Boot 4.1.x as a **mandatory post-delivery task** (DL-07, progressive delivery).
  - **Workaround:** VMware Spring Support contracts offer extended security patches for 3.5.x, but are out of scope for this evaluation.
```

### Después (Consequences/Negative)
```
- **No free security patches after 2026-06-30** (already past).
  - **Development/Evaluation scope:** This baseline is authorized for development, evaluation, and non-production integration only (3-day brief window).
  - **Production options:**
    1. **Commercial support:** Upgrade to VMware Spring Support contract for Boot 3.5.x security patches (eligibility requires contract terms).
    2. **Open-source support:** Migrate to Boot 4.1.x or later (requires upgrade planning and testing; post-delivery decision).
  - This ADR does NOT mandate Boot 4.1.x as the sole path; it permits either option depending on production requirements and support availability.
```

**Trazabilidad:** Requirements.md DEC-12, R-02 (Boot 3.5 EOL) → ADR-10 (scope clarified) → platform-foundation (development-only baseline)

---

## CAMBIO 2: proposal.md — Done Criteria Verificables

**Archivo:** `openspec/changes/platform-foundation/proposal.md`

### Antes (Done Criterion)
```
✅ Platform builds and runs locally:
1. Both services compile on Java 21 with Gradle 8.14.8.
2. Both services start in docker-compose and report readiness (`/health/ready` returns 200).
3. CI workflow triggers on PR to `develop`; passes build and unit tests.
```

### Después (Done Criterion)
```
✅ Platform builds and runs locally:
1. Both services compile on Java 21 with Gradle 8.14.8: 
   ./gradlew build from each service root returns SUCCESS (exit code 0, no errors).
2. docker-compose up -d from repo root completes; docker-compose ps shows all services with 
   status "healthy" (not "up" or "starting") within 30 seconds.
3. CI workflow triggers on PR to develop: GitHub Actions workflow completes successfully 
   (all steps pass); build and unit tests pass.
```

### Antes (Data layer)
```
✅ Data layer is ready:
1. `vidacruz_triage` and `vidacruz_assistant` PostgreSQL databases are created and migrated by Flyway.
2. `AuditoriaClinica` table schema exists (columns: `id`, `timestamp`, `pacienteId`, `estadoAnterior`, `estadoNuevo`, `tipoEvento`).
3. Integration test verifies that `AuditoriaClinica` rejects UPDATE and DELETE (RBAC or database constraint).
```

### Después (Data layer)
```
✅ Data layer is ready:
1. After docker-compose up -d and successful startup, verify: 
   psql -h localhost -U postgres -d vidacruz_triage -c "SELECT COUNT(*) FROM AuditoriaClinica" 
   returns 0 rows (table created). Repeat for vidacruz_assistant with FragmentoDocumental.
2. AuditoriaClinica table exists with columns (verify via schema query): 
   id UUID, timestamp TIMESTAMP, pacienteId UUID, estadoAnterior VARCHAR, estadoNuevo VARCHAR, tipoEvento VARCHAR.
3. Integration test T6.1 verifies RBAC: connects via R2DBC (application user), 
   executes INSERT → SUCCESS; executes UPDATE → ERROR "permission denied"; 
   executes DELETE → ERROR "permission denied". Test runs with app user from R2DBC pool, not postgres superuser.
```

### Antes (Motivation)
```
Motivation

The brief specifies Spring Boot 3.x; the provided skeletons default to Boot 4.x. ADR-10 accepts Boot 3.5.x with the understanding that open-source support ended June 2026 (already passed). This change:
1. Aligns with the brief requirement.
2. Avoids Java 25 + Gradle 9.x compatibility risk.
3. Establishes the platform so other changes can begin in parallel.
4. Proves the CI pipeline works before feature development.
```

### Después (Dependencies + Motivation)
```
Dependencies

Requires formal acceptance of:
- ADR-10 (Spring Boot 3.5.16 baseline, development/evaluation scope)
- ADR-14 (One PostgreSQL database per service)
- ADR-07 (API key authentication)
- ADR-05 (Spring AI 1.1.x GA for LLM)
- ADR-06 (Webhook notifications with outbox retry)

Current status: All five ADRs are Proposed. This change is conditional on their acceptance. 
If any ADR is rejected or significantly modified, platform-foundation must be revisited.

Motivation

The brief specifies Spring Boot 3.x; the provided skeletons default to Boot 4.x. ADR-10 accepts Boot 3.5.x for development/evaluation (with production options documented). This change:
1. Aligns with the brief requirement for the evaluation window.
2. Avoids Java 25 + Gradle 9.x compatibility risk.
3. Establishes the platform so other changes can begin in parallel.
4. Proves the CI pipeline works before feature development.
```

**Trazabilidad:** Requirements.md NFR-01, NFR-05, NFR-12, NFR-18, TC-01…TC-04 → proposal.md (done criteria verificables) → tasks.md (measurable tests)

---

## CAMBIO 3: tasks.md — Scope Correction (T6.4 removido, T7.4 agregado)

**Archivo:** `openspec/changes/platform-foundation/tasks.md`

### Antes
```
Task Checklist (Phase 6): 
- T6.1, T6.2, T6.3, T6.4 (Redis), T6.5 (BlockHound)

Done criterion:
- 15 tasks total
```

### Después
```
Task Checklist (Phase 6): 
- T6.1, T6.2, T6.3, [T6.4 REMOVED — belongs to change 9], T6.5 (BlockHound)

Task Checklist (Phase 7):
- T7.1, T7.2, T7.3, T7.4 (NEW: Verify production restriction in README)

T6.4 note:
~~T6.4: Assistant Service Redis Integration Test~~ [REMOVED — belongs to change 9: emergency-state-projection]
- Redis testing is out of scope for platform-foundation.
- Redis is used only by the Assistant for urgency projection (change 9).
- T6.4 will be implemented in change 9 when the projection feature is built.

T7.4 (NEW):
**T7.4: Verify Production Restriction in README**
- Edit README.md to document:
  - Clear warning: "This baseline is for development and evaluation only. 
    Production deployments require either VMware Spring Support contract for Boot 3.5.x 
    or migration to Boot 4.1.x+ (open-source support)."
  - Scope restrictions explicitly stated
  - Production path options documented
  - Reference to ADR-10 for details
- Verification: README explicitly states production restriction and both path options 
  (commercial support for 3.5.x OR migration to 4.1.x).

Done criterion (revised):
- 14 tasks total (T6.4 deferred to change 9)
```

**Trazabilidad:** Proposal.md (done criteria) → tasks.md (measurable tasks) → T7.4 (ADR-10 scope verification)

---

## CAMBIO 4: spec.md — Restricción Production Visible

**Archivo:** `openspec/changes/platform-foundation/spec.md`

### Antes (inicio del documento)
```
# OpenSpec Change 1: Platform Foundation — Detailed Specification

**Change ID:** `platform-foundation`
**Component:** Both services
**Date:** 2026-11-25

---

## 1. Build Configuration
```

### Después (inicio del documento)
```
# OpenSpec Change 1: Platform Foundation — Detailed Specification

**Change ID:** `platform-foundation`
**Component:** Both services
**Date:** 2026-11-25

---

## ⚠️ PRODUCTION RESTRICTION

**This specification is authorized for development, evaluation, and non-production integration only.**

**Production deployments require ONE of these conditions:**
1. **VMware Spring Support contract** with current security patch commitment for Spring Boot 3.5.x, OR
2. **Migration to Spring Boot 4.1.x or later** (open-source support; planned post-delivery)

For production use, refer to ADR-10 for support path options.

---

## 1. Build Configuration
```

**Trazabilidad:** ADR-10 (scope, production options) → spec.md (warning box, desbloqueo conditions)

---

## CAMBIO 5: REVIEW-BLOCKERS.md — Documento de Auditoría

**Archivo:** `openspec/changes/platform-foundation/REVIEW-BLOCKERS.md` (NEW)

**Contenido:** Auditoría crítica de 5 bloqueadores identificados por usuario:
- ✅ ADR-05 colisión (RESUELTO)
- 🔴 ADR-10 producción (CRÍTICO — corregido en cambio 1)
- ✅ ADR-14 requisitos (RESUELTO)
- 🔴 Done criteria (CRÍTICO — corregido en cambio 2)
- 🟡 15 tareas Redis (IMPORTANTE — corregido en cambio 3)

**Trazabilidad:** Auditoría → 6 cambios autorizados → este documento

---

## Matriz de Trazabilidad de los 6 Cambios

| # | Cambio | Archivo | Requisito/ADR | Done Criterion | Verificable |
|---|---|---|---|---|---|
| **1** | ADR-10: Scope flexible | ADR-10 | DEC-12, R-02 | Production options documented | README + ADR-10 |
| **2** | Done Criteria precisas | proposal.md | NFR-01…04, TC-01…04 | 3 criteria verificables (Gradle, docker-compose, CI) | Tests T6.1, T7.2 |
| **3** | Scope: T6.4 → T7.4 | tasks.md | ADR-10 | T6.4 removed, T7.4 added | Tasks checklist |
| **4** | Production restriction | spec.md | ADR-10 | Warning box top of spec | README verification |
| **5** | Dependencias ADRs | proposal.md | ADR-10…06 | 5 ADRs declared Proposed | Conditional acceptance |
| **6** | Auditoría crítica | REVIEW-BLOCKERS.md | User review | 6 blockers resolved | Commit 32928a6 |

---

## Autorización y Restricciones

✅ **Cambios AUTORIZADOS por:**
- User approval (email/chat message)
- Respected conditions: flexible production options, verifiable criteria, scope correction, explicit ADR dependencies, production restrictions

✅ **Cambios COMPLETADOS:**
- All 6 changes applied
- Commit 32928a6 created (documentation only)
- No code, no push, no PR, no merge

⏳ **Paralela:** Otra sesión revisa spec.md + design.md independientemente

---

## Próximos Pasos

1. ✅ Esta sesión: 6 cambios completos (documentación)
2. ⏳ Otra sesión: Continúa revisión spec.md + design.md
3. ⏳ Ambas sesiones cierran
4. 🎯 **Autorización explícita:** `sdd-apply platform-foundation` (pendiente ambas revisiones)

