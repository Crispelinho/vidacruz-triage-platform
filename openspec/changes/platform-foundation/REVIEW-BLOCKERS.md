# Platform Foundation — Critical Review Before sdd-apply

**Date:** 2026-11-25  
**Reviewer:** User  
**Status:** BLOCKERS IDENTIFIED — Do not proceed with sdd-apply until resolved

---

## 1. ADR-05 Colisión ✅ RESUELTO

**Hallazgo:** ADR-05 en adr-roadmap.md ya define "LLM provider and Spring AI versus LangChain4j".  
**Mi ADR-05:** "LLM provider and framework — Spring AI 1.1.x GA"  
**Verificación:** DEC-06 en requirements.md (línea 440) dice exactamente: "LLM and RAG via Spring AI 1.x behind an interface; provider chosen by configuration; mocked in tests"

**Conclusión:** ✅ **No hay colisión.** Mi ADR-05 implementa correctamente DEC-06. Es el ADR esperado.

---

## 2. ADR-10: Spring Boot 3.5.16 — Restricción de No-Producción ⚠️ CRÍTICO

**Problema:** ADR-10 acepta Boot 3.5.16 (EOL 2026-06-30, ya pasado), pero NO **documenta explícitamente** la restricción:

> "La excepción temporal que habíamos contemplado permitía desarrollo e integración no productiva, pero **no producción por defecto**."

**En ADR-10 actual (línea 48–62):**
```
Decision: Adopt Spring Boot 3.5.x (latest patch, verify on spring.io) on Java 21 with Gradle 8.x.
```

**Falta:** Una cláusula explícita: "**This baseline is for development and evaluation only. Production deployments must upgrade to Boot 4.1.x or later.**"

**En platform-foundation tasks:** Las tareas debe incluir:
- **T7.4 (nuevo):** Verificación: "README documenta que build es SOLO para evaluación/desarrollo"
- **Done Criterion (actualizado):** Incluir restricción: "Build is authorized for development/evaluation; production deployments require Boot 4.1.x"

**Acción requerida:** 
1. Actualizar ADR-10 Consequences → Negative section para agregar:
   ```
   - **Production restriction:** This baseline is authorized only for development and evaluation (3-day brief window). 
     Production deployments must upgrade to Boot 4.1.x or later before going live.
     - **Verification:** README explicitly documents this restriction.
   ```

2. Actualizar ADR-10 Decision section para agregar:
   ```
   **Scope:** This decision applies to development, evaluation, and the 3-day brief window only. 
   Production deployments are out of scope and must use Boot 4.1.x or later.
   ```

3. Agregar T7.4 a tasks.md.

---

## 3. ADR-14: One PostgreSQL per Service — ¿Contradice requisitos? ⚠️ VERIFICAR

**Requisito a verificar:** ¿Hay requisitos de acceso cross-service que ADR-14 rompe?

**Lectura de requirements.md:**
- RF-04 Part B (API-05): "Built only from `triaje.eventos` (no calls to the Triage API)"
- DEC-10: "One PostgreSQL database per service; Redis only for the Assistant projection"
- NFR-04: "Triage Service never depends on Assistant availability (no synchronous HTTP or SQL calls to Assistant)"

**Verificación:** ✅ ADR-14 es **consistente**. Requisitos explícitamente prohíben cross-service calls.

**Conclusión:** ✅ **No hay contradicción.**

---

## 4. Done Criterion — Verificabilidad ⚠️ CRÍTICO

**Problema:** Done criteria en proposal.md (línea 60–80) tienen declaraciones no verificables:

| Criterion | Problema | Verificable? |
|---|---|---|
| "Both services build on Java 21" | ✓ Verificable: `./gradlew build` → SUCCESS | ✅ |
| "containers start" | ❌ Ambiguo: ¿cómo se prueba? ¿timeout? | ⚠️ |
| "all reach healthy within 30s" | ⚠️ ¿30s es suficiente? ¿cuál es el healthcheck? | ⚠️ |
| "health probes respond 200 UP" | ✓ Verificable: `curl http://localhost:8081/health/ready` | ✅ |
| "audit table is append-only" | ⚠️ Cómo se prueba: UPDATE y DELETE fallan, ¿pero con qué usuario? | ⚠️ |
| "CI pipeline passes" | ✓ Verificable: GitHub Actions workflow succeeds | ✅ |

**Criterios a refinar en proposal.md:**

1. **"containers start"** → Cambiar a:
   ```
   "docker-compose up -d" succeeds; docker-compose ps shows all services with status "up"
   ```

2. **"reach healthy within 30s"** → Cambiar a:
   ```
   After "docker-compose up -d", wait 30s and run:
   - docker-compose ps | grep -E "triage-service|clinical-assistant" | grep healthy
   Both services show "healthy" status (not "up" or "starting")
   ```

3. **"audit table is append-only"** → Cambiar a:
   ```
   Integration test T6.1 verifies:
   - INSERT into AuditoriaClinica succeeds
   - UPDATE on AuditoriaClinica returns: ERROR: permission denied for relation
   - DELETE on AuditoriaClinica returns: ERROR: permission denied for relation
   Test runs with application user (from R2DBC pool), not postgres superuser
   ```

**Acción requerida:** Actualizar proposal.md Done Criterion section (línea 60–80) con criterios verificables.

---

## 5. OpenSpec y Trazabilidad — ¿15 tareas mapeadas? ⚠️ VERIFICAR

**Problema:** tasks.md tiene 15 tareas, pero ¿están todas mapeadas a requisitos/decisiones?

**Auditoría de trazabilidad:**

| Tarea | Requisito/Decisión | Verificable | Estado |
|---|---|---|---|
| T1.1 (Gradle Triage) | TC-01, NFR-01 | ✓ Build succeeds | ✅ |
| T1.2 (Gradle Assistant) | TC-01, NFR-01 | ✓ Build succeeds | ✅ |
| T1.3 (Gradle Root) | TC-01 | ✓ Subprojects resolved | ✅ |
| T2.1 (Flyway Triage) | DER-04, TC-03 | ✓ Schema loads; audit append-only | ✅ |
| T2.2 (Flyway Assistant) | TC-03, TC-07 (pgvector) | ✓ Schema loads; pgvector active | ✅ |
| T3.1 (Config Triage) | NFR-08, NFR-09 | ✓ Actuator responds; SLAs configured | ✅ |
| T3.2 (Config Assistant) | NFR-08, NFR-09 | ✓ Actuator responds; AI provider set | ✅ |
| T3.3 (docker-compose.yml) | NFR-12 | ✓ All services reach healthy | ✅ |
| T4.1 (Dockerfile Triage) | NFR-12 | ✓ Image builds; starts | ✅ |
| T4.2 (Dockerfile Assistant) | NFR-12 | ✓ Image builds; starts | ✅ |
| T4.3 (Alert receiver mock) | RF-06 (future), ADR-06 | ✓ Receives POST, responds 200 | ✅ |
| T5.1 (GitHub Actions CI) | NFR-18 | ✓ Workflow triggers; passes | ✅ |
| T6.1 (Triage integration test) | NFR-15, TC-03 | ✓ DB connectivity; audit append-only | ✅ |
| T6.2 (Kafka test) | TC-04 | ✓ Producer/consumer work | ✅ |
| T6.3 (Assistant DB test) | TC-03, TC-07 | ✓ pgvector loads | ✅ |
| T6.4 (Redis test) | ADR-08 (future), TC-07 (implied) | ⚠️ Redis no es requisito de platform-foundation | ⚠️ |
| T6.5 (BlockHound) | NFR-01 | ✓ No blocking I/O | ✅ |
| T7.1 (README) | NFR-18 (implied) | ✓ Document ready | ✅ |
| T7.2 (Verify acceptance) | Proposal done criteria | ✓ Checklist | ✅ |
| T7.3 (Change summary) | — | ✓ Document | ✅ |

**Hallazgo:** T6.4 (Redis test) está **fuera de scope de platform-foundation**:
- Redis es para el Assistant (ADR-08, cambio 9)
- platform-foundation solo necesita: PostgreSQL, Kafka, Gradle, Docker, CI
- Incluir Redis en platform-foundation complica la verificación

**Acción requerida:** Remover T6.4 de tasks.md para platform-foundation. Redis test pertenece a cambio 9 (emergency-state-projection).

---

## 6. Propuestas vs. Decisiones Formales ⚠️ CLARIFICACIÓN

**Problema:** Los cinco ADRs están en status **"Proposed"**, no "Accepted".

**En proposal.md (línea 1):**
```
**Status:** Proposed
```

**En spec.md y design.md:** Presentan propuestas como hechos establecidos (ej: "Usar pgvector"). 

**Clarificación necesaria:**
- ADR-10, ADR-14, ADR-07, ADR-05, ADR-06 son **Proposed**, no Accepted
- platform-foundation spec/design/tasks presuponen que estos ADRs serán aceptados
- Si un ADR es rechazado/modificado, platform-foundation debe replantearse

**Acción requerida:** Agregar nota al inicio de proposal.md:
```
⚠️ **Status:** This change proposes the platform-foundation capability.
Its implementation depends on acceptance of ADR-10, ADR-14, ADR-07, ADR-05, ADR-06.
If any ADR is rejected or significantly modified, this proposal must be revisited.
```

---

## Resumen de Cambios Requeridos

| # | Documento | Cambio | Prioridad |
|---|---|---|---|
| 1 | ADR-10 | Agregar restricción explícita: "Development/evaluation only, not production" | 🔴 CRÍTICO |
| 2 | proposal.md | Refinar Done Criteria con verificabilidad concreta | 🔴 CRÍTICO |
| 3 | tasks.md | Remover T6.4 (Redis test, fuera de scope) | 🟡 IMPORTANTE |
| 4 | tasks.md | Agregar T7.4: Verificar que README documenta restricción production | 🔴 CRÍTICO |
| 5 | proposal.md | Agregar nota de dependencia: "Requiere aceptación de 5 ADRs" | 🟡 IMPORTANTE |
| 6 | spec.md | Verificar que restricción production se refleja en sección de riesgos | 🟡 IMPORTANTE |

---

## Recomendación

**NO proceder con sdd-apply hasta:**

1. ✅ Resolver cambio #1 (ADR-10: restricción production)
2. ✅ Resolver cambio #2 (proposal.md: Done Criteria verificables)
3. ✅ Resolver cambio #4 (tasks.md: T7.4 nueva tarea + T6.4 removida)
4. ✅ Resolver cambio #5 (proposal.md: nota de dependencia ADRs)
5. ✅ Verificar cambios #3, #6 (spec.md, design.md)

**Tiempo estimado para resolver:** 1–2 horas de edits + revisión.

**Después:** Autorizar explícitamente `sdd-apply platform-foundation`.

---

## Próximo paso

¿Autorizas que corrija estos 6 puntos en los documentos?
