# Criterios de Aprobación — Stakeholder
## Change 1: Gates G-01, G-02, G-03

**Fecha:** 2026-11-25  
**Reviewr:** Stakeholder  
**Ámbito:** ADR-01 (Service Decomposition), ADR-10 (Spring Boot 3.5), ADR-14 (Database Ownership)

---

## 🎯 Objetivo de la Revisión

Evaluar si los 3 ADRs cumplen requisitos de negocio/arquitectura para autorizar Change 1 (platform-foundation) en Day 1 (Nov 26).

---

## ✅ Gate G-01: ADR-01 (Service Decomposition)

### Pregunta decisiva:
**¿Aceptar exactamente 2 servicios (Triage + Clinical Assistant) con integración async Kafka para C-01?**

### Requisitos de aprobación:
- [ ] Scope es claro: 2 servicios, Kafka async como único canal
- [ ] No hay sincronía entre servicios (RF-04-B, NFR-04)
- [ ] Riesgos aceptables documentados
- [ ] Verificación (4 tests) es realista
- [ ] No depende de otros ADRs sin resolver

### Riesgos a considerar:
- Eventual consistency en proyección del Assistant (aceptable para summary-only use)
- Transacciones distribuidas imposibles (aceptable per design)
- Escalado independiente de servicios (feature, no bug)

### Mi recomendación:
✅ **ACCEPT** — Scope está bien delimitado, dependencias claras

---

## ✅ Gate G-02: ADR-10 (Spring Boot 3.5.16)

### Pregunta decisiva:
**¿Aceptar Spring Boot 3.5.16 para evaluación/desarrollo CON plan de migración a Boot 4.1 post-delivery?**

### Requisitos de aprobación:
- [ ] Alcance claro: "evaluation/dev only" (no producción)
- [ ] EOL status entendido: Boot 3.5 EOL passed 2026-06-30 ✅ (aceptado)
- [ ] Mitigación: Plan Boot 4.1 post-delivery documentado ✅
- [ ] Timeline realista: 2-3 weeks para migración post-delivery ✅
- [ ] CI/CD gate Java 21 implementado ✅

### Riesgos a considerar:
- **CRÍTICO:** Boot 3.5 ya está en EOL (fin de soporte pasado)
- Mitigación: Post-delivery obligatoria (contractual)
- No compromete Day 1 prototype (3 días, eval scope)
- Boot 4.1 PoC debe estar en Day 2 planning

### Mi recomendación:
✅ **ACCEPT (CON CAVEATS)**
- Condición 1: Alcance estrictamente "evaluation/dev"
- Condición 2: Boot 4.1 PoC incluido en Day 2 planning
- Condición 3: NO usar Boot 3.5 para cualquier ambiente post-eval

---

## ✅ Gate G-03: ADR-14 (Database Ownership)

### Pregunta decisiva:
**¿Aceptar una PostgreSQL por servicio con Kafka como canal de sincronización?**

### Requisitos de aprobación:
- [ ] Dependencia de G-01: "Must accept G-01 first" ✅
- [ ] Schema ownership separado (Flyway independiente) ✅
- [ ] No hay queries cross-service (Kafka es único canal) ✅
- [ ] Deferral OD-5: Decisión de retención de EventoHistorico postergada ✅
- [ ] Verificación realista: 3 tests claros ✅

### Riesgos a considerar:
- **CONDICIONAL:** OD-5 (EventoHistorico retention) debe decidirse ANTES de producción
- Riesgo de acumulación de datos históricos sin límite
- Mitigación: Ticket OD-5 creado y priorizado post-delivery

### Mi recomendación:
✅ **ACCEPT (CONDICIONAL)**
- Condición 1: G-01 DEBE aceptarse primero
- Condición 2: Crear ticket OD-5 inmediatamente post-delivery (antes de producción)
- Condición 3: Clinical retention review por equipo legal requerida

---

## 📋 Matriz de Decisión

| Gate | ADR | Pregunta | Recomendación | Condiciones |
|------|-----|----------|---|---|
| **G-01** | ADR-01 | ¿2 servicios + async Kafka? | ✅ ACCEPT | Ninguna |
| **G-02** | ADR-10 | ¿Boot 3.5 eval + Boot 4.1 post? | ✅ ACCEPT | Eval/dev scope + PoC Boot 4.1 Day 2 |
| **G-03** | ADR-14 | ¿One-DB per service? | ✅ ACCEPT | G-01 first + OD-5 ticket pre-prod |

---

## ✍️ Decisión del Stakeholder

**Fecha revisión:** 2026-11-25  
**Autoridad decisora:** Stakeholder (usuario del proyecto)  
**Veredictos registrados:** 3/3 completos

---

**G-01 (ADR-01): Service Decomposition**
- [x] **ACCEPT** ✅
- [ ] ACCEPT CON CONDICIONES
- [ ] RECHAZAR

**Decisión:** Acepta dos servicios (Triage + Clinical Assistant) con integración asíncrona Kafka.

---

**G-02 (ADR-10): Spring Boot 3.5.16 Baseline**
- [ ] ACCEPT
- [x] **ACCEPT CON CONDICIONES** ✅
- [ ] RECHAZAR

**Condiciones explícitas:**
1. Spring Boot 3.5.16 **solo para evaluación/desarrollo** (3-day prototype)
2. Proof-of-Concept Boot 4.1 migration incluido en **Day 2 planning**
3. **Migración obligatoria a Boot 4.1** antes de cualquier ambiente post-eval (contractual)

---

**G-03 (ADR-14): Database Ownership**
- [ ] ACCEPT
- [x] **ACCEPT CON CONDICIONES** ✅
- [ ] RECHAZAR

**Condiciones explícitas:**
1. **Prerequisito:** G-01 debe estar ACCEPT (confirmado ✅)
2. **OD-5 ticket:** Crear **inmediatamente post-entrega** y antes de producción (retención de EventoHistorico)
3. **Legal review:** Revisión de retención clínica requerida antes de go-live

---

## ⏱️ Siguiente Paso

**Estado actual:** G-01, G-02, G-03 = ACCEPT (2 con condiciones)
- Change 1 está **HABILITADO** para Day 1 implementation (Nov 26)
- **Pre-requisitos:** ADR-02 (159 líneas via PR #25) y ADR-03 gaps deben estar VALIDADOS primero

**Cambios dependientes:**
- Change 2 (event-driven-integration) requiere ADR-03 gaps resueltos
- Change 3 (clinical-assistant-rag) pospuesto post-deployment a nube

---

## ⚠️ IMPORTANTE

**Esta aprobación NO autoriza automáticamente:**
- Mergear todos los ADRs
- Cambiar el repositorio sin PR
- Iniciar implementación sin validar pre-requisitos (ADR-02, ADR-03 gaps)
- Usar Boot 3.5 fuera de scope evaluation/dev

**Cada paso requiere autorización expresa después de esta aprobación.**
