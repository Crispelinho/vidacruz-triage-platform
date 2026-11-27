# VidaCruz — Architecture Diagrams

Status: **draft for review.** Diagrams are derived from `docs/requirements.md` (RF, RN, API, EVT, §11 persistence), `docs/adr-roadmap.md` and the service docs. Where a diagram depends on an ADR that is not yet accepted, the label says so.

## 1. Logical architecture

```mermaid
flowchart LR
    Client["Nurse / Clinician client"]
    subgraph Triage["triage-service (WebFlux, R2DBC)"]
        TAPI["Triage API<br/>API-01, API-02, API-03, API-06"]
        SLA["SLA evaluator<br/>RF-03, RN-03"]
        TOUT["Outbox relayer<br/>(ADR-03)"]
    end
    subgraph Assistant["clinical-assistant-service (WebFlux)"]
        AAPI["Assistant API<br/>API-04, API-05"]
        PROJ["Urgency projection consumer<br/>(ADR-08)"]
        RAG["RAG pipeline<br/>(ADR-04, ADR-05)"]
    end
    KAFKA[("Kafka<br/>triaje.eventos")]
    TPG[("PostgreSQL<br/>triage DB")]
    APG[("PostgreSQL + pgvector<br/>assistant DB")]
    REDIS[("Redis<br/>urgency projection")]
    LLM["LLM provider<br/>(ADR-05, Proposed)"]
    MED["Medical team alert channel<br/>(RF-06, ADR-06, Proposed)"]

    Client --> TAPI
    Client --> AAPI
    TAPI --> TPG
    SLA --> TPG
    TPG --> TOUT
    TOUT --> KAFKA
    SLA --> KAFKA
    KAFKA --> PROJ
    PROJ --> REDIS
    AAPI --> REDIS
    AAPI --> RAG
    RAG --> APG
    RAG --> LLM
    KAFKA --> MED
    Triage -. "no synchronous call (NFR-04)" .-> Assistant
```

## 2. Patient registration sequence (RF-01, EVT-01)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant T as Triage API
    participant DB as Triage PostgreSQL
    participant O as Outbox relayer
    participant K as Kafka triaje.eventos
    C->>T: POST /triage/pacientes (+ optional Idempotency-Key)
    T->>T: Validate VAL-01..VAL-11, classify (RN-01), compute SLA (RN-02)
    T->>DB: BEGIN
    T->>DB: INSERT Paciente (estado = REGISTRADO)
    T->>DB: INSERT AuditoriaClinica (RN-04)
    T->>DB: INSERT OutboxEvento + EventoHistorico (paciente_registrado)
    T->>DB: COMMIT
    T-->>C: 201 {id, nivel, slaMinutos, estado, fechaRegistro}
    O->>DB: Read pending OutboxEvento
    O->>K: Publish paciente_registrado (key = pacienteId)
    K-->>O: ACK
    O->>DB: Mark OutboxEvento published (publicadoEn)
```

Notes: the response does not wait for Kafka. Pending events are retried by the relayer. The `Idempotency-Key` path (DEC-15) returns the stored response without a second insert.

## 3. Escalation with concurrency guard (RF-03, RN-03)

```mermaid
sequenceDiagram
    autonumber
    participant S1 as SLA evaluator (replica A)
    participant S2 as SLA evaluator (replica B)
    participant DB as Triage PostgreSQL
    participant K as Kafka
    S1->>DB: UPDATE Paciente SET escalado = true WHERE id = ? AND escalado = false AND estado = 'REGISTRADO'
    S2->>DB: same conditional UPDATE
    DB-->>S1: 1 row (winner)
    DB-->>S2: 0 rows (no-op)
    S1->>DB: INSERT AuditoriaClinica (tipoEvento = ESCALAMIENTO) + outbox row
    S1->>K: paciente_escalado (via outbox)
    Note over S2: publishes nothing, writes no audit row
```

## 4. Patient state machine (RF-02, RN-04)

`escalado` is a separate flag. It does not change `estado`.

```mermaid
stateDiagram-v2
    [*] --> REGISTRADO: POST /triage/pacientes
    REGISTRADO --> EN_CONSULTA: PATCH estado = EN_CONSULTA
    REGISTRADO --> ALTA: PATCH estado = ALTA (direct discharge)
    EN_CONSULTA --> ALTA: PATCH estado = ALTA
    ALTA --> [*]
    note right of ALTA: ALTA → EN_CONSULTA returns 409
```

## 5. Event flow and projection (EVT-01..EVT-04, DER-03)

```mermaid
flowchart TD
    E1["paciente_registrado"] --> P["Assistant projection consumer"]
    E2["paciente_escalado"] --> P
    E3["paciente_en_consulta"] --> P
    E4["paciente_alta"] --> P
    E2 --> A["Alert consumer (RF-06)"]
    P --> D{"eventId already in<br/>ProcessedEvent?"}
    D -- yes --> SKIP["Ignore duplicate"]
    D -- no --> SEQ{"pacienteSequence<br/>greater than last seen?"}
    SEQ -- no --> SKIP
    SEQ -- yes --> UPD["Update Redis counts per level"]
    UPD --> MARK["Store eventId in ProcessedEvent (TTL)"]
```

## 6. RAG query flow (RF-04 A, RN-05)

```mermaid
flowchart TD
    Q["POST /asistente/consulta"] --> V{"Question empty<br/>or whitespace?"}
    V -- yes --> B400["400"]
    V -- no --> R["Retrieve chunks from pgvector<br/>(metadata filter by protocolo)"]
    R --> G{"Any chunk passes<br/>relevance threshold?"}
    G -- no --> NOTICE["Fixed notice:<br/>'no existe protocolo disponible'<br/>fuentes: []<br/>(LLM not called)"]
    G -- yes --> L{"LLM circuit closed<br/>and call succeeds?"}
    L -- no --> B503["503 LLM_UNAVAILABLE"]
    L -- yes --> C{"Every citation exists<br/>in corpus and text has a citation?"}
    C -- no --> NOTICE
    C -- yes --> OK["200 respuesta + fuentes"]
```

## 7. Circuit breaker states on LLM calls (NFR-03, ADR-09 Proposed)

```mermaid
stateDiagram-v2
    [*] --> Closed
    Closed --> Open: failure rate over threshold
    Open --> HalfOpen: wait duration elapsed
    HalfOpen --> Closed: trial calls succeed
    HalfOpen --> Open: trial call fails
    Open --> Open: requests fail fast with 503
```

## 8. Decision acceptance order

Source: `docs/adr-roadmap.md` §3.3 (line 70). This is an order, not a full dependency graph. The roadmap does not state edges between ADRs; it states which change needs which ADR ("Must be accepted before", §3). All 14 ADRs are currently `Proposed`, except that the scope of ADR-12 was accepted by the user.

```mermaid
flowchart LR
    A01["ADR-01"] --> A10["ADR-10"] --> A14["ADR-14"] --> A03["ADR-03"] --> A02["ADR-02"] --> A13["ADR-13"] --> A07["ADR-07"] --> A12["ADR-12"]
    A12 --> A04["ADR-04"] --> A05["ADR-05"] --> A09["ADR-09"] --> A08["ADR-08"] --> A06["ADR-06"] --> A11["ADR-11"]
```
