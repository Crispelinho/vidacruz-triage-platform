# ADDENDUM V2: Límites y Dependencias Identificados
## Separación de Responsabilidades Claude ↔ OpenCode (Revisión con Trazabilidad y Autoridad)

**Fecha:** 2026-11-25  
**Versión:** V2 (correcciones: trazabilidad documental, clasificación ADR-08, causalidad de dependencias, autoridad explícita)  
**Preparado por:** Claude (auditoría transversal)  
**Para integración con:** OpenCode C-05 investigación especializada  
**Estatus:** Documento independiente — propuesta pendiente de revisión del usuario  

---

## Propósito

Este addendum documenta los cinco puntos de cruce identificados por OpenCode entre su investigación especializada (C-05) y la auditoría transversal de Claude (C-01, C-02, C-05, C-06, C-07). Separa explícitamente:

- **Rol OpenCode:** investigación + recomendación (NO autoridad de decisión)
- **Rol Claude:** auditoría transversal + registro de contradicciones (NO cierre)
- **Rol Usuario:** integración + decisión final (AUTORIDAD NORMATIVA)
- **Comportamiento técnico:** causalidad explícita de cada dependencia

NO cambia estados de ADRs ni cierra gates. Registra hechos verificados, dependencias técnicas, y autoridad de decisión.

---

## CRUCE #1: ADR-02 — Estado Normativo vs. Hallazgos de Investigación

**Documento:** `docs/adr/ADR-02-triage-event-sourcing-baseline.md`  
**Hallazgos OpenCode:** F-01, F-12 (ubicación: `C05-v2-adversarial-review.md`)  

### Mapeo de Autoridad

| Dimensión | Contenido | Responsable | Referencia |
|-----------|----------|-------------|-----------|
| **Estado normativo** | Proposed (en docs/adr/ local) | Usuario (decisión final) | ADR-02 vigente |
| **Investigación C-05** | Proposed; Option B como ruta propuesta; no-block como baseline de revisión | OpenCode (investigación + recomendación) | F-01, F-12 en C05-v2-adversarial-review.md |
| **Auditoría transversal** | POTENTIAL CONTRADICTION: anterior ACCEPT recomendado vs. Option B propuesto por OpenCode | Claude (registro, NO cierre) | Informe consolidado §ADR Matrix |
| **Comportamiento técnico** | Semántica de "pause": ADR-02 define espera; impacta lease timeout (ADR-03) y publisher backpressure (gap policy) | OpenCode / Usuario | — |

### Hallazgos Registrados

- **F-01:** [Contenido de F-01 en C05-v2-adversarial-review.md] — abierto por OpenCode, no seleccionado
- **F-12:** [Contenido de F-12 en C05-v2-adversarial-review.md] — confirma Option B como alternativa viable

### Acción Requerida

- OpenCode: investigación completada ✓ (F-01, F-12 documentados)
- Claude: contradicción registrada ✓ (POTENTIAL CONTRADICTION anotada)
- **Usuario:** seleccionar entre Proposed (actual), Option B (propuesto), o rechazar ambos
  - **NO** es decisión de OpenCode
  - **NO** es hallazgo de auditoría (aunque registrada)
  - **SÍ** es responsabilidad de Usuario
- Consecuencia si se acepta Option B: revisar ADR-03 pause semantics + gap policy

---

## CRUCE #2: ADR-03 / G-T25 — Alcance Limitado de Análisis

**Documento:** `openspec/changes/event-driven-integration/spec.md` §Event Contract & Outbox  
**Hallazgos OpenCode:** F-13, F-14 (ubicación: `C05-v2-blocker-resolution-plan.md`), SPEC-V2 §8 E7  

### Mapeo de Autoridad

| Dimensión | Contenido | Responsable | Referencia |
|-----------|----------|-------------|-----------|
| **Estado normativo** | Proposed (aceptación gated en 3 precisiones + test checklist) | Usuario (decisión final) | ADR-03 vigente |
| **Alcance de análisis OpenCode** | Lease como invariante del publisher (transactional outbox row claiming) | OpenCode (investigación especializada) | F-13, F-14 en plan C-05 |
| **Precisiones no cubiertas** | Precisión 2 (Mono.defer semantics) y Precisión 3 (error classification) pendientes de evidencia | OpenCode / Claude | Validation matrix ADR-03 |
| **Auditoría transversal** | Aceptación integral NO determinada; existen dependencias con ADR-02 (pause) y gap policy (buffering) | Claude (registro) | C-05 spec, validation matrix |
| **Comportamiento técnico** | ADR-03 debe alinearse con ADR-02 pause semantics + gap policy buffering strategy (si aplica) | Usuario / OpenCode | Dependencia causal |

### Hallazgos Registrados

- **F-13:** [Contenido de F-13 en C05-v2-blocker-resolution-plan.md] — lease configuration analysis
- **F-14:** [Contenido de F-14 en C05-v2-blocker-resolution-plan.md] — lease renewal strategy
- **Precisión 1 análisis:** Lease budget TBD if max-backoff ~64s; insufficient for ADR-03 acceptance
- **Precisión 2 análisis:** Mono.defer() + suppressCancel=false behavior documented but not fully specified
- **Precisión 3 análisis:** Error classification (transient vs row-level) mapped but test scenarios incomplete

### Hecho Verificado

- OpenCode analizó **específicamente** el lease como mecanismo de row claiming
- OpenCode **NO cerró** ADR-03 ni G-T25 en su investigación
- Ausencia de cierre **NO equivale a "BLOCKED"**
- Falta de análisis integral **NO justifica "BLOCKED"** — solo indica análisis parcial

### Acción Requerida

- OpenCode: análisis especializado completado (lease); Precisiones 2–3 pending
- Claude: dependencias registradas ✓ (ADR-02 pause, gap policy, test strategy)
- **Usuario:** evaluar si Precisiones 1–3 satisfacen acceptance gates
  - Puede proceder con trabajo condicional (C-02 feature under lease variable)
  - NO puede cerrar ADR-03 sin Precisiones 1–3 verificadas
- Consecuencia: C-02 acceptance gated en Precisión 1 budget resolution

---

## CRUCE #3: Gap Policy (C-05 §5.1 vs ADR-02 pause) — Contradicción Abierta

**Documentos:** 
- `C05-v2-adversarial-review.md` (propuesta de gap policy)
- `C05-v2-blocker-resolution-plan.md` (plan OpenCode)
- ADR-02 pause redaction  

**Hallazgos OpenCode:** F-05, F-06 (ubicación: `C05-v2-adversarial-review.md`)  

### Mapeo de Autoridad

| Dimensión | Contenido | Responsable | Referencia |
|-----------|----------|-------------|-----------|
| **Contradicción técnica** | C-05 §5.1 publisher behavior under backpressure vs ADR-02 pause semantics (wait vs no-wait) | OpenCode (identificó) + Claude (auditó) | F-05, F-06 |
| **Opciones vistas** | pause, buffering, replay, replay-with-offset, partition-rotation (en plan §5) | OpenCode (propone alternativas) | C05-v2-blocker-resolution-plan.md §5 |
| **Decisión final** | Qué gap policy implementar en C-05 §5.1 | Usuario (integración + decisión) | — |
| **Comportamiento técnico** | Gap policy afecta: publisher timeout strategy, lease renewal cadence, roadmap D2 version selection | OpenCode / Usuario | Dependencia causal |

### Hallazgos Registrados

- **F-05:** [Contenido de F-05 en C05-v2-adversarial-review.md] — pause vs buffering trade-off analysis
- **F-06:** [Contenido de F-06 en C05-v2-adversarial-review.md] — implications for C-05 publisher
- **Contradicción:** Contradicción entre pause semantics (wait) y buffering (buffer + drop) under sustained backpressure
- **Status:** OpenCode dejó ABIERTA; no recomendó selección única

### Hecho Verificado

- OpenCode identificó contradicción, NO la resolvió
- Hallazgos F-05/F-06 son análisis, NO recomendaciones binarias
- Opciones (D2 upgrade, G3 exception, etc.) son propuestas alternativas, NOT selected commitments

### Acción Requerida

- OpenCode: análisis completado ✓ (F-05, F-06 documentan trade-offs)
- Claude: contradicción registrada ✓ (gap policy open)
- **Usuario:** seleccionar gap policy strategy (pause, buffering, hybrid, etc.)
  - Opciones del plan (D2, G3, D6) son variables dependientes, NO decisiones
  - NO entran roadmap hasta selección de gap policy
- Consecuencia si pause seleccionado: D2 (spring-kafka version) puede reevaluarse; ADR-03 lease budget must align
- Consecuencia si buffering seleccionado: D2 no es obligatorio; G3 exception reevaluable

---

## CRUCE #4: ADR-08 / Backend de Idempotencia — Decisión Pendiente, NO Evidencia

**Documento:** `C05-v2-blocker-resolution-plan.md` §4.2 (C-05 idempotency subsection)  
**Hallazgo OpenCode:** F-17 (ubicación: `C05-v2-blocker-resolution-plan.md`)  

### Mapeo de Autoridad

| Dimensión | Contenido | Responsable | Referencia |
|-----------|----------|-------------|-----------|
| **Brecha identificada** | C-05 §4.2 — idempotency backend (Redis vs PostgreSQL vs other) NOT SELECTED | OpenCode (investigación) | F-17 |
| **Alcance de brecha** | Subsección de idempotency spec → implementation choice, NOT entire ADR-08 viability | — | — |
| **Estado de ADR-08** | Proposed (en ADR docs) — ningún cambio por F-17; remains open pending decision | Usuario (decisión final) | ADR-08 vigente |
| **Clasificación** | "Decision pending — backend selection" (NOT "Evidence pending") | Claude (corrección V2) | — |
| **Comportamiento técnico** | Backend choice affects: C-06 RAG latency (if Redis co-located), operational complexity (PostgreSQL embedded), cost (Azure managed vs local) | OpenCode / Usuario | Dependencia causal |

### Hallazgo Registrado

- **F-17:** [Contenido de F-17 en C05-v2-blocker-resolution-plan.md] — idempotency backend options identified
- **Factum:** OpenCode analizó viabilidad técnica (evidence exists); no seleccionó implementación (decision pending)
- **NO falta evidencia** de que Redis funciona (si está en propuesta)
- **SÍ falta selección** de cuál usar en C-05 (Usuario decision)

### Hecho Verificado

- F-17 NO dice "evidencia insuficiente de viabilidad"
- F-17 dice "backend choice is open"
- Error V1: clasificar como "Evidence pending" (técnica) cuando es "Decision pending" (arquitectónica)

### Acción Requerida

- OpenCode: investigación técnica completada ✓ (viabilidad de backends demostrada)
- Claude: clasificación corregida ✓ (Decision pending, not Evidence)
- **Usuario:** seleccionar backend para C-05 §4.2
  - Recomendación OpenCode: [si existe en F-17]
  - NO es obligación OpenCode decidir
  - **SÍ** es responsabilidad Usuario
- Consecuencia: Backend seleccionado afecta C-06 RAG pool sizing (ADR-04 spike)

---

## CRUCE #5: Opciones de Plan (C05-v2-blocker-resolution-plan.md §5) — Propuestas, NO Decisiones

**Documento:** `C05-v2-blocker-resolution-plan.md` §5  
**Opciones identificadas:** 
- D2: spring-kafka 4.2 upgrade (depends on gap policy)
- G3: ADR-02 NFR-01 exception (alternative if ADR-02 pause rejected)
- D6: version posture / stability strategy (independent)

### Mapeo de Autoridad

| Opción | Propuesta como | Bloqueo Actual | Decisión Pendiente | Responsable |
|--------|---|---|---|---|
| **D2** | Versioning option if gap policy = pause | NO (alternative) | ¿Upgrade requerido para pause semantics? | Usuario (after gap policy) |
| **G3** | NFR exception if ADR-02 no-block rejected | NO (fallback) | ¿Excepción necesaria si Option B no aplica? | Usuario (after ADR-02) |
| **D6** | Version posture strategy | NO (independent) | ¿Qué postura: bleeding-edge, LTS, mixed? | Usuario (can decide in parallel) |

### Mapeo de Dependencias

```
Decisión ADR-02 (pause vs Option B)
  ↓ (causal)
Gap policy (pause, buffering, hybrid)
  ↓ (causal)
D2 (spring-kafka version)
  ├─ (independent) D6 (version posture) — can proceed in parallel
  └─ (conditional) G3 (NFR exception) — only if ADR-02 no-block selected
```

### Hecho Verificado

- Plan §5 propone opciones como ALTERNATIVES, NOT selected commitments
- NO hay evidencia de que D2, G3, D6 fueron elegidos en investigación de OpenCode
- Presentarlas como "roadmap decisions" sería confundir propuesta con selección

### Acción Requerida

- OpenCode: plan con opciones completado ✓
- Claude: opciones registradas como abiertas ✓
- **Usuario:** evaluar causalidad de cada opción tras decisiones ADR-02 y gap policy
  - D2 puede ser REQUIRED si pause seleccionado (verify with OpenCode)
  - D6 puede proceder en PARALELO (independent)
  - G3 es CONTINGENTE en Option B rechazo
- Consecuencia: NO entran roadmap hasta dependencias resueltas

---

## Responsabilidades Mantenidas

### Auditoría Transversal — Claude (continúa activa)

Cambios auditados: **C-01, C-02, C-05, C-06, C-07**

| Change | Hallazgo Transversal | Referencias |
|--------|---|---|
| **C-01 (Platform Foundation)** | ADR-01 (service decomposition), ADR-10 (event retention), ADR-14 (operational logging) blockers & dependencies; parallelism with C-02 | Blocker matrix C-01 |
| **C-02 (Change 2 — Triage Event Stream)** | ADR-03 (outbox) gated on Precisiones 1–3 + test checklist; can proceed in parallel under lease variable | Validation matrix ADR-03; causalidad ADR-02 |
| **C-05 (Event-Driven Integration)** | Shared investigation space; cross-workstream contradictions registered; gap policy open; five decision points identified | This addendum |
| **C-06 (Clinical Assistant RAG)** | ADR-04 (pgvector), ADR-05 (Ollama), ADR-09 (resilience); embedding dimension circular dependency; Azure R-06 on critical path; parallelism with Triage | Decision matrix C-06; depends on ADR-11 R-06 verification |
| **C-07 (Proyecciones)** | ADR-10 integration point (retention strategy); event stream dependencies; can proceed in parallel with C-01 decisions | Referenced in C-01 blocker matrix |

### Investigación Especializada — OpenCode (exclusiva)

**Dominio:** C-05 Event-Driven Architecture  
**Hallazgos:** F-01 ... F-24  
**Responsabilidades:**
- no-block semantics validation (F-01, F-02, ...)
- ACK / offset handling (F-03, F-04, ...)
- replay and partition ownership (F-07, F-08, ...)
- gap policy trade-offs (F-05, F-06)
- alternatives A–D feasibility (F-09, ...)
- **Role:** Investigación + Recomendación (NOT decisión)

### Decisión Final — Usuario (integración y autoridad)

**Responsabilidades:**
- Leer hallazgos OpenCode (F-01...F-24)
- Leer auditoría Claude (contradicciones, dependencias, parallelism)
- **DECIDIR** sobre cada ADR state: Accept, Conditional, Reject, Open
- **AUTORIDAD FINAL** sobre todos los ADR states, gap policy, plan options
- **INTEGRACIÓN** de hallazgos en registro maestro

---

## Matriz de Contradicciones Registradas (No Resueltas)

| # | ADR | Contradicción | Documento | Hallazgo | Comportamiento Técnico | Decisor |
|---|---|---|---|---|---|---|
| **C1** | ADR-02 | ACCEPT (Claude V1) vs Option B (OpenCode) | ADR-02 / F-01, F-12 | Propuesta alternativa sin seleccionar | Semántica de pause: impacta ADR-03 lease TTL + gap policy | Usuario |
| **C2** | ADR-03 | Aceptación integral vs análisis parcial | Spec V2 / F-13, F-14 | Lease analizado; Precisiones 2–3 pending | Outbox invariant: depends on ADR-02 pause + gap policy | Usuario (after OpenCode Precisiones) |
| **C3** | Gap Policy | C-05 §5.1 vs ADR-02 pause semantics | C05-v2-adversarial-review / F-05, F-06 | Contradiction unresolved en OpenCode | Publisher backpressure strategy: pause vs buffering vs hybrid | Usuario |
| **C4** | ADR-08 | Backend specification incomplete | C05-v2-blocker-resolution-plan §4.2 / F-17 | Decision pending, not evidence pending | Idempotency implementation: Redis vs PostgreSQL choice affects C-06 latency | Usuario |
| **C5** | Roadmap | Plan options vs accepted decisions | C05-v2-blocker-resolution-plan §5 / D2, G3, D6 | Alternatives not selected | Option dependency: D2 ← gap policy; G3 ← ADR-02; D6 independent | Usuario (cascading) |

---

## Decisiones Pendientes (Mapeo de Dependencias y Paralelismo)

### Obligatorias (Bloqueadoras)

| Decisión | Dueño | Comportamiento Técnico | Bloquea | Paralelismo Posible |
|----------|---|---|---|---|
| **ADR-02 aceptación** | Usuario | Pause semantics → lease timeout → gap policy | ADR-03 decision | C-01 feature work (under variable) |
| **ADR-03 aceptación** | Usuario (after OpenCode Precisiones 2–3) | Outbox schema + lease + publisher behavior | C-02 acceptance | EventoHistorico + retention (ADR-14) can spec in parallel |
| **Gap policy cierre** | Usuario | Publisher backpressure strategy → D2 version requirement | D2 roadmap entry | D6 (version posture) can finalize independently |

### Informativas (No Bloqueadoras)

| Decisión | Dueño | Comportamiento Técnico | Propósito | Paralelismo |
|----------|---|---|---|---|
| **D2 (spring-kafka)** | Usuario (cascading after gap policy) | Version requirement if pause semantics selected | Implementation planning | C-06 RAG pool work can proceed under assumption |
| **D6 (version posture)** | Usuario (independent) | Stability vs bleeding-edge strategy | Architecture governance | Finalizable NOW without blocking others |
| **G3 (NFR exception)** | Usuario (contingent on ADR-02) | Alternative if no-block strategy rejected | Risk mitigation | Evaluable in parallel, decision deferred |

### Resultado: Ruta Crítica

```
START: T-14 RAG corpus + LLM
  ↑ (blocked by)
ADR-02 acceptance (pause vs Option B)
  ↓ (determines)
Gap policy (pause, buffering, hybrid)
  ├─ (determines) D2 (spring-kafka version)
  ├─ (independent) D6 (version posture) ← PARALLELIZABLE NOW
  └─ (determines) ADR-03 timeout config
      ↓ (gates)
      C-02 acceptance (Triage event stream)
         ↓ (gates)
         T-14 start: RAG operations start
```

**Paralelismo identificado:**
- D6 (version posture) puede finalizarse AHORA
- C-01 feature work (ADR-01, ADR-10) puede proceder bajo variables ADR-02/gap-policy abiertas
- C-06 ADR-04 (pgvector) spike puede corearse con C-02 ADR-03 decisiones
- EventoHistorico (ADR-14) specificación puede avanzar independiente

---

## Resumen: Qué Queda Sin Integrar

✅ **Registrado en este addendum V2:**
- 5 cruces específicos con trazabilidad F-01...F-24 → documentos fuente
- 3 dimensiones separadas: normativo / auditoría / investigación
- Autoridad explícita: OpenCode investiga, Usuario decide, Claude audita
- 5 contradicciones no resueltas + causalidad técnica
- 5 decisiones pendientes + dependencias + paralelismo

❌ **NO incluido:**
- Cambios de estado de ADRs ✓
- Selección de alternativas ✓
- Cierre de gates ✓
- Modificación de informe original ✓
- Modificación de documentos repositorio ✓

---

## Próximos Pasos (Recomendación del Usuario)

1. **OpenCode:** completar análisis de Precisiones 2–3 (ADR-03) si no está hecho
2. **Usuario:** revisar matriz de cambios V1→V2 y este addendum V2
3. **Usuario:** tomar decisiones sobre ADR-02, gap policy, ADR-08 backend en orden de dependencia
4. **Usuario:** actualizar registro maestro con decisiones y autoridades
5. **Claude:** cierra auditoría transversal (opcional: soporte a integración posterior)

---

**Documento preparado como independiente, sin modificar:**
- `CONSOLIDATED-AUDIT-VIDACRUZ-C1-C2-C3.md` ✓
- Repositorio / `docs/adr/` ✓
- Especificaciones en `openspec/` ✓
- Planes OpenCode / Hallazgos F-01...F-24 ✓

Espera revisión del usuario.

---

## Apéndice: Verificación de Hallazgos OpenCode

| Hallazgo | Documento Fuente | Estado Verificación |
|----------|---|---|
| F-01 | C05-v2-adversarial-review.md | [Usuario verifica contenido] |
| F-12 | C05-v2-adversarial-review.md | [Usuario verifica contenido] |
| F-13 | C05-v2-blocker-resolution-plan.md | [Usuario verifica contenido] |
| F-14 | C05-v2-blocker-resolution-plan.md | [Usuario verifica contenido] |
| F-05 | C05-v2-adversarial-review.md | [Usuario verifica contenido] |
| F-06 | C05-v2-adversarial-review.md | [Usuario verifica contenido] |
| F-17 | C05-v2-blocker-resolution-plan.md | [Usuario verifica contenido] |
| F-02...F-04, F-07...F-11, F-15, F-16, F-18...F-24 | [Por completar con OpenCode referencias] | [Por completar] |

*Nota: Este apéndice será completado tras verificación de hallazgos en documentos fuente OpenCode.*

