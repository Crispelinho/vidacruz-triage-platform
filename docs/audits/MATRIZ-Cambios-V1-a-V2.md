# MATRIZ DE CAMBIOS: Addendum V1 → V2

**Fecha:** 2026-11-25  
**Revisión:** Independiente (sin modificar V1)  
**Estado:** Propuesta pendiente de revisión del usuario

---

## Resumen Ejecutivo

| Aspecto | V1 | V2 | Razón |
|--------|----|----|-------|
| Trazabilidad de hallazgos | Genérica (F-01...F-24 mencionados) | Documento fuente + sección verificada | Rigor: cada hallazgo debe ser rastreable a evidencia |
| Clasificación ADR-08 | "Evidence pending" | "Decision pending — backend selection" | Precisión: falta selección (ej: Redis vs PostgreSQL), no falta evidencia técnica |
| Dependencias | Enumeradas; ¿causalidad? | Mapeadas a comportamiento técnico concreto + paralelismo | Claridad: distinguir bloqueos reales de relaciones informativas |
| Autoridad de decisión | Implícita | Explícita: OpenCode = investigación; Usuario = decisión final | Gobernanza: recuperar separación de responsabilidades |

---

## 1. TRAZABILIDAD DOCUMENTAL Y HALLAZGOS

### Cambio: Mapeo Explícito F-01...F-24 a Fuentes

**V1:** "OpenCode F-01, F-12" (sin origen documento)

**V2:** Estructura de cada hallazgo:
```
Hallazgo: F-01
Documento fuente: [archivo OpenCode donde aparece F-01]
Sección: [referencia dentro]
Contenido: [qué dice F-01 exactamente]
Atribución: [es hallazgo original de OpenCode o reorganización de plan]
```

**Acción específica:**
- Para cada cruce, verificar si F-01, F-12, F-13, etc. aparecen en:
  - `C05-v2-adversarial-review.md` (hallazgos OpenCode)
  - `C05-v2-blocker-resolution-plan.md` (plan OpenCode)
  - `C05-v2-blocker-resolution-plan-v2.md` (si existe versión actualizada)
- Distinguir: "OpenCode encontró" vs "plan organiza"
- NO atribuir a V2 del plan lo que no he comprobado en ella

**Riesgo evitado:**
- Lectura de hallazgos de un documento en otro por transferencia implícita

---

## 2. CLASIFICACIÓN ADR-08: DE "EVIDENCE" A "DECISION"

### Cambio Puntual: Renombrar Estado Pendiente

**V1:**
```
Estado de ADR-08: Proposed
Brecha identificada: Backend (Redis vs PostgreSQL) NO fijado
Clasificación: "Evidence pending"
```

**V2:**
```
Estado de ADR-08: Proposed (sin cambios)
Brecha identificada: C-05 §4.2 idempotency backend NOT SELECTED
Clasificación: "Decision pending — backend selection (Redis vs PostgreSQL vs other)"
Hallazgo F-17: Backend implementation choice TBD in C-05 investigation
```

**Razón:**
- F-17 NO dice "falta evidencia técnica de viabilidad"
- F-17 dice "backend no está seleccionado"
- La decisión es arquitectónica (cuál elegir), no empírica (si funciona)
- OpenCode debe recomendar (evidence), Usuario decide (selection)

**Acción:**
- Revisar F-17 literalmente para confirmar "selection" vs "evidence"
- Si F-17 dice "evidence pending": mantener en V2 como está
- Si F-17 dice "decision pending": cambiar clasificación

---

## 3. DEPENDENCIAS: DE ENUMERACIÓN A CAUSALIDAD TÉCNICA

### Cambio: Mapeo Explícito de Comportamiento Técnico

**V1:**
```
| Decisión | Dueño | Bloqueadores | Dependientes |
| ADR-02 Option B | User | F-01/F-12 | ADR-03 pause decision |
```

**V2:**
```
| Decisión | Comportamiento Técnico | Bloqueo Obligatorio | Paralelismo Posible |
| ADR-02 Option B aceptación | Semántica de triage event (sourced vs outbox pause) | SÍ → ADR-03 debe alinearse con decisión | C-01/C-02 feature work puede iniciar bajo variable |
| ADR-03 pause semantics | Semántica de row claiming + lease lifetime | SÍ → cadencia de publisher, C-02 acceptance | Spike de pooling (ADR-04) puede corearse |
| Gap policy (pause vs buffering) | Publisher behavior under load | INFORMATIVA → es derivada, no bloqueadora | Versioning strategy (D6) puede finalizarse |
```

**Acciones concretas:**

#### 3.1 ADR-02 → ADR-03: ¿Obligatoria o Informativa?
- **Comportamiento técnico:** ADR-02 define semántica de "pause" (wait until ready). ADR-03 debe implementar timeout respetando esa semántica.
- **Bloqueo:** SÍ — si ADR-02 cambia "pause" a "no-block", la tolerancia de ADR-03 lease-timeout cambia.
- **Paralelismo:** C-01 platform foundation (ADR-01, ADR-10) puede corearse; C-02 publisher puede desarrollarse bajo variable.

#### 3.2 ADR-03 → C-02 Acceptance: ¿Obligatoria o Informativa?
- **Comportamiento técnico:** ADR-03 specifica outbox schema, publisher claiming, lease TTL. C-02 acceptance criteria incluye "outbox working end-to-end".
- **Bloqueo:** SÍ — C-02 no acepta sin ADR-03 implementado.
- **Paralelismo:** EventoHistorico (ADR-14) y retention (OPEN-09/10) pueden especificarse en paralelo.

#### 3.3 Gap Policy → Roadmap: ¿Obligatoria o Informativa?
- **Comportamiento técnico:** Gap policy (pause vs buffering) es una decisión de comportamiento del publisher bajo backpressure.
- **Bloqueo:** INFORMATIVA — gap policy influye en D2 (spring-kafka version) pero no bloquea roadmap. Plan puede listar opciones sin seleccionar.
- **Paralelismo:** D6 (versioning strategy) puede finalizarse independiente. ADR-02 Option B y ADR-03 pueden coexistir con variables abiertas.

**Hecho clave:**
- NO confundir "depende de" con "está bloqueado por"
- Dependencias informativas permiten avance paralelo bajo supuestos

---

## 4. AUTORIDAD DE DECISIÓN: CLARIFICACIÓN DE ROLES

### Cambio: Mapear Responsabilidades Explícitamente

**V1:**
```
Auditoría Claude: potencial contradicción
Hallazgos OpenCode: F-01, F-12
Acción requerida: registrar contradicción
```

**V2:**
```
OpenCode role (investigación C-05):
  - Analiza ADR-02 Option B viabilidad
  - Recomienda no-block vs pause trade-off (F-01, F-12)
  - NO acepta ni rechaza ADR-02 (no es autoridad)
  
Claude role (auditoría transversal):
  - Verifica coherencia entre ADR-02 y ADR-03 en C-01/C-02/C-05
  - Registra contradicción potencial (ACCEPT vs Option B)
  - NO cierra la contradicción; la deja marcada
  
Usuario role (integración y decisión):
  - Lee hallazgos de OpenCode (F-01...F-24)
  - Lee auditoría de Claude (contradicciones, dependencias)
  - DECIDE si ADR-02 acepta, rechaza u opta por Option B
  - AUTORIDAD FINAL sobre ADR states
```

**Mapeo completo de autoridad:**

| Decisión | OpenCode | Claude | Usuario |
|----------|----------|--------|---------|
| ADR-02 Option B viabilidad | ✓ Investiga / Recomienda | ✓ Audita coherencia | ✓✓ DECIDE |
| ADR-03 lease budget | ✓ Analiza precisión 1 | ✓ Audita con ADR-02 | ✓✓ DECIDE |
| Gap policy (pause vs buffering) | ✓ Analiza trade-offs | ✓ Registra contradicción | ✓✓ DECIDE |
| ADR-08 backend selection | ✓ Recomienda Redis/PG | ✓ Verifica impacto C-06 | ✓✓ DECIDE |
| Plan opciones cierre | ✓ Propone D2/G3/D6 | ✓ Verifica alternativas | ✓✓ DECIDE |

**Acción V2:**
- Reescribir cada cruce con roles explícitos
- Marcar claramente qué es "investigación", qué es "auditoría", qué es "decisión pendiente"
- NO permitir que recomendaciones de OpenCode se lean como decisiones

---

## Tabla Resumida: V1 → V2

| Cruce | V1 Riesgo | V2 Corrección |
|-------|-----------|---|
| **Trazabilidad** | Hallazgos flotantes sin fuente verificable | Mapeo F-01...F-24 → documento + sección |
| **ADR-08** | Ambigüedad "Evidence" (técnica?) vs "Decision" (selección?) | Precisar: "Decision pending — backend selection" |
| **Dependencias** | Enumeradas sin causalidad; ¿bloquean realmente? | Mapeo a comportamiento técnico + identificación de paralelismo |
| **Autoridad** | Roles implícitos; ¿quién decide? | Explícito: OpenCode investiga, Usuario decide, Claude audita |

---

## Próximos Pasos

1. Verificar F-01...F-24 en documentos fuente OpenCode
2. Reescribir Addendum V2 con trazabilidad, roles, y causalidad técnica
3. Entregar matriz de cambios + V2 para revisión usuario
4. NO modificar V1 ni informe consolidado

