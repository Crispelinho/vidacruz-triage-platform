# Change 3: Dependencias y Gates actualizadas

## 1. ADR Gates para Change 3

(openspec-roadmap D4, implementation-roadmap D6, actualizado post-ADR-05 decision)

| Gate | ADR | Capacidad | Decide | Estado | Pre-requisito para | Bloqueador |
|------|-----|-----------|--------|--------|-------------------|-----------|
| G-09a | ADR-05 | C-06 RAG | LLM provider (Spring AI vs LangChain4j) | ✅ **DECIDED: Spring AI 1.1.1 + Ollama** | T-14 (Day 1) | ❌ Ninguno |
| G-09b | ADR-09 | C-06 RAG | Circuit Breaker + Model B fallback | ⏳ **Propuesta** (ver adr-09-circuit-breaker-proposal.md) | T-15 (Day 2) | Model B validation pass |
| G-09c | ADR-04 | C-06 RAG | Vector store (pgvector) | ⏳ **Propuesta** | T-14 (Day 1) | ❌ Ninguno (independent) |
| G-09d | ADR-08 | C-07 Projection | Projection store (Redis) | ⏳ **Propuesta** | T-16 (Day 2) | ❌ Ninguno (independent) |

---

## 2. Cadena de dependencias para Change 3

### C-06 (Clinical Assistant RAG) — Ruta crítica

```
Day 1 start
│
├─ Gate G-09a: ADR-05 DECIDED ✅
│  └─ Prerequisite: T-14 (Implementación RAG)
│
├─ Gate G-09c: ADR-04 Propuesta ⏳
│  └─ Prerequisite: T-14 (Implementación de pgvector)
│     └─ Note: Independiente de ADR-05, puede definirse en paralelo
│
├─ T-14: Implementar RAG + corpus (Day 1)
│  └─ Dependencies: ADR-05 (Spring AI), ADR-04 (pgvector)
│     └─ Deliverable: PROTO queries retornan respuestas validadas
│
├─ T-15: Implementar circuit breaker (Day 2)
│  └─ Prerequisites: ADR-09 Propuesta + Model B validation
│     └─ Gate G-09b: ADR-09 acceptance (antes de T-15)
│        └─ Blocker: Model B equivalence validation
│
└─ Day 1 exit: T-14 completa con PROTO queries green
   Day 2 exit: T-15 completa con circuit breaker + fallback tested
```

### C-07 (Clinical Assistant Projection) — Paralela

```
Day 1 start
│
├─ Gate G-09d: ADR-08 Propuesta ⏳
│  └─ Prerequisite: T-16 (Proyección desde eventos)
│
├─ Dependency: C-05 (Event-driven integration)
│  └─ Prerequisite: T-04 (Outbox writer + EVT-01…EVT-04)
│     └─ Note: Day 1 entrada (Change 2 task)
│
├─ T-16: Consumidor de eventos + Proyección (Day 2)
│  └─ Dependencies: ADR-08 (Redis), C-05 event contract
│     └─ Prerequisite: T-04 debe estar completo
│
└─ T-17: GET /asistente/estado-urgencias (Day 2)
   └─ Prerequisite: T-16 projection completa
```

---

## 3. Topología de ADRs para Change 3

```
Orden de aceptación (adr-roadmap D3 §3.3):

ADR-01 (Service decomposition)
  ↓
ADR-10, ADR-14, ADR-03, ADR-02
  ↓
ADR-13, ADR-07, ADR-12
  ↓
[Assistant Decisions] ← CHANGE 3 STARTS HERE
├─ ADR-04 (pgvector) ← Independent
├─ ADR-05 (LLM provider) ← DECIDED ✅
│  └─ ADR-09 (Circuit Breaker) ← Depends on ADR-05
└─ ADR-08 (Redis projection)
  ↓
[Last Decisions]
├─ ADR-06 (Webhook channel)
└─ ADR-11 (Azure deployment) ← NOT a blocker for Change 3
```

**Implicación:** ADR-11 es POSTERIOR a Change 3. No bloquea timeline de Change 3.

---

## 4. Gates y secuencia en 3 días

### Day 1 (Foundations + Outbox + Start RAG)

**Gates que deben estar aceptadas antes de Day 1:**
- ✅ G-09a (ADR-05): Spring AI 1.1.1 + Ollama DECIDIDO
- ⏳ G-09c (ADR-04): pgvector PROPUESTA (puede revisarse Day 1 mañana)

**Tareas:**
- T-01 (baseline): Spring Boot, Java 21, Gradle (ambos servicios) ← Prerequisite para T-14
- T-02 (Testcontainers): PostgreSQL + pgvector, Kafka
- T-04 (Event outbox): EVT-01…EVT-04 contract
- **T-14 (RAG):** Corpus + retrieval + LLM primario (Spring AI + Ollama)

**Exit criteria Day 1:**
- ✅ T-14: PROTO-001, PROTO-002, diabetes query retornan respuestas
- ✅ Corpus embedded en pgvector
- ✅ Spring AI integrado con Ollama
- ⏳ Circuit breaker NOT YET (needs ADR-09 + Model B validation)

### Day 2 (Triage lifecycle + SLA + RAG fallback + Projection)

**Gates que deben estar aceptadas antes de Day 2 tarde:**
- ⏳ G-09b (ADR-09): Propuesta con Model B validation PASS ← BLOCKER if validation fails
- ⏳ G-09d (ADR-08): Redis projection PROPUESTA

**Tareas:**
- T-09, T-10 (Triage lifecycle): State transitions
- T-11, T-12 (SLA evaluator + escalation)
- **T-15 (Circuit breaker):** Resilience4j + Model B fallback (requires G-09b)
- **T-16, T-17 (Projection):** Redis consumer + GET estado-urgencias

**Exit criteria Day 2:**
- ✅ T-14, T-15: RAG con fallback, ambos circuit breakers testeados
- ✅ T-16, T-17: Proyección desde eventos, GET /estado-urgencias funciona
- ✅ Correlación ID propagada (optional, nice-to-have)

### Day 3 (Security + Observability + Delivery)

**Gates:**
- ADR-07 (autenticación): Debe estar aceptada antes de T-19

**Tareas:**
- T-19 (Auth): API key en `/asistente/**`
- T-20, T-21 (Health, metrics)
- T-22, T-23, T-24 (Docker, Kubernetes, Azure o blocker)
- T-25 (README + decisions index)

**Exit criteria Day 3:**
- ✅ Endpoints autenticados
- ✅ Health probes y métricas
- ✅ Deployment path (Azure si disponible, o documented blocker)

---

## 5. Cambios respecto a exploración anterior

| Aspecto | Exploración 2026-11-25 | Actualizado 2026-11-25 (post-ADR-05) |
|--------|---|---|
| ADR-05 status | ⏳ Propuesta, decisión pendiente | ✅ **DECIDED: Spring AI 1.1.1** |
| ADR-09 status | ⏳ Propuesta | ⏳ **Propuesta con arquitectura** (ver adr-09-circuit-breaker-proposal.md) |
| ADR-04 blocker | Ninguno | ❌ Ninguno (independiente) |
| ADR-08 blocker | Ninguno | ❌ Ninguno (independiente) |
| Paralelización | C-06 en paralelo con Triage | ✅ **Confirmado: sin bloqueadores cruzados** |
| ADR-11 impact | Posterior, no bloquea C-06 | ✅ **Confirmado: no bloquea C-06 ni C-07** |

---

## 6. Bloqueadores REALES (reducidos)

| Bloqueador | Status | Timeline | Mitigación |
|-----------|--------|----------|-----------|
| **ADR-09 propuesta** | ⏳ En revisión | Hoy | Documento adr-09-circuit-breaker-proposal.md listo para decisión |
| **Model B validation** | ⏳ Pre-requisito para G-09b | Pre-Day 2 | Protocolo de validación documentado en adr-09-model-equivalence-validation.md |
| **Ollama setup** | ✅ Testcontainers (T-02) | Day 1 | docker-compose + init script |
| **Azure (ADR-11)** | ⚠️ R-06 blocker | Day 3 (T-24) | No bloquea Days 1-2; grabar blocker en README si no disponible |

---

## 7. Decisiones pendientes para developer

| Decisión | Requerida por | Timeline | Owner |
|----------|---|---|---|
| **Model B selection** (ej: mistral vs neural-chat) | ADR-09 acceptance | Pre-Day 2 | Developer |
| **Circuit breaker thresholds** (failure rate, timeout) | T-15 config | Day 1-2 | Developer (con ADR-09) |
| **Redis endpoint** (localhost vs cloud) | T-16 | Day 2 | Developer (con ADR-08) |
| **API key mechanism** (env var, Kubernetes Secret) | ADR-07 / T-19 | Day 3 | Developer (ADR-07 scope) |

---

## 8. Checklist de preparación de Change 3

### Pre-Day 1
- [ ] ADR-05 DECIDED ✅ (spring AI 1.1.1)
- [ ] ADR-04 Propuesta (pgvector)
- [ ] ADR-09 Propuesta con 3 opciones + recomendación
- [ ] ADR-08 Propuesta (Redis)
- [ ] Model B elegido (pre-validation)
- [ ] Corpus (PROTO-001, 002, 003) preparado

### Day 1
- [ ] T-01: Baseline (Spring Boot 3.5.16, Java 21)
- [ ] T-02: Testcontainers con PostgreSQL+pgvector, Kafka
- [ ] T-04: Outbox writer (prerequisite para T-14)
- [ ] T-14: RAG primario (Spring AI + Ollama)
- [ ] Model B validation PASS ✅
- [ ] ADR-09 acceptance

### Day 2
- [ ] T-15: Circuit breaker + Model B fallback
- [ ] T-16, T-17: Projection + GET estado-urgencias
- [ ] Both circuit breakers testeados

### Day 3
- [ ] T-19: API key auth
- [ ] T-20, T-21: Health + metrics
- [ ] T-22-25: Docker + deployment

---

**Nota:** Change 3 está LISTO para propuesta formal una vez ADR-04, ADR-08, ADR-09 tengan propuestas + Model B validation prepare.
