# Matriz de cumplimiento — Enunciado Prueba Técnica Dev Webflux

**Fecha:** 2026-11-27  
**Repositorio:** https://github.com/Crispelinho/vidacruz-triage-platform  
**Rama evaluada:** `develop` / release `v1.0.2` promovido a `main`  
**Alcance:** verificación documental y de evidencia en código/tests; no incluye redespliegue Azure ni ejecución E2E en esta sesión.

## Leyenda

| Estado | Significado |
|---|---|
| **PASS** | Evidencia directa encontrada en repo/docs/tests. |
| **PARCIAL** | Implementación/evidencia parcial, o depende de ejecución/entorno externo. |
| **GAP** | No hay evidencia suficiente en el estado actual. |

## 1. Entregables

| Requisito | Estado | Evidencia | Nota |
|---|---|---|---|
| Repositorio en GitHub | PASS | `README.md`, remoto `origin` GitHub, PRs/issues históricos | Repo público/compartible según configuración. |
| README: un comando para ejecutar | PASS | `README.md` → `cp .env.example .env` + `docker compose up -d --build`; `docker-compose.yml` levanta apps + infra | Compose incluye triage, assistant, DBs, Kafka, Redis, Prometheus, Grafana, Jaeger. |
| README: diagrama de arquitectura | PASS | `README.md` sección “Architecture” con Mermaid + enlace a `docs/architecture/diagrams.md` | El diagrama principal queda visible en README. |
| README: tecnologías | PASS | `README.md` tabla “Technologies” | Incluye Java 21, Boot 3.5.16, WebFlux, R2DBC, Kafka, Redis, Resilience4j, Spring AI/pgvector, OTel, Testcontainers, Docker/K8s/Azure. |
| README: decisiones clave y justificación | PASS | `README.md` tabla “Key decisions” + `docs/adr/README.md` | Enlaza ADRs canónicos y justificación. |
| Pruebas automatizadas | PASS | 105 archivos `*Test*.java`/IT; CI en `.github/workflows/*.yml`; Testcontainers/BlockHound en builds | Cobertura específica mapeada abajo. |
| URL Azure desplegada y APIs accesibles | PARCIAL | URL de entrega provista: `https://vidacruz-l7rsr6.northcentralus.cloudapp.azure.com/docs/triage/swagger-ui/index.html#/Pacientes/registrarPaciente`; probe sin credenciales devolvió `401` (gateway/filtro API key alcanzable). `docs/operations/despliegue-azure-y-pruebas.md`, `infra/azure/`, `infra/k8s/`, `.github/workflows/deploy.yml` | **No incluir la API key en repo/correo público**; la disponibilidad puede variar por control de costos. Falta evidencia autenticada en esta sesión. |
| Manifiestos Kubernetes / IaC Azure | PASS | `infra/k8s/`, `triage-service/deployment/k8s/`, `clinical-assistant-service/deployment/k8s/`, `infra/azure/main.bicep` | Kustomize + Bicep + deploy workflow. |
| Mínimo 2 ADRs | PASS | `docs/adr/` (ADR-01..15 y anexos) | Se supera el mínimo. |

## 2. Requisitos funcionales

| ID | Estado | Evidencia principal | Evidencia de pruebas / verificación |
|---|---|---|---|
| RF-01 Registro y clasificación | PASS | `triage-service/.../application/controller/TriageController.java`, `.../domain/service/TriageClassifier.java`, `.../application/dto/*`, `docs/requirements.md` RF-01 | `TriageControllerTest`, `domain/service/TriageClassifierTest` (casos E1-E6), `docs/demo/local-verification.md` sección RF-01 |
| RF-02 Flujo de estado y consulta | PASS | `PatientLifecycleService`, `TriageController`, `InvalidStateTransitionException`, auditoría/outbox en transición | `PatientLifecycleServiceTest` (409 transición inválida), `TriageSLAEscalationIntegrationTest` transiciones EN_CONSULTA/ALTA, `EventEnvelopeValidationTest` EVT-03/04 |
| RF-03 Monitoreo SLA y escalamiento | PASS | `SlaEvaluator`, `SlaEvaluationScheduler`, `SlaProperties`, `MedicalAlert*`, outbox EVT-02, migraciones alertas | `TriageSLAEscalationIntegrationTest` (overdue once, no doble escalamiento, concurrencia, REGISTRADO only), `OperationalReportsIntegrationTest` SLA |
| RF-04 Asistente clínico RAG + estado urgencias | PASS | `clinical-assistant-service/.../RAGService`, `RAGController`, `GroundedCitationValidator`, `RedisUrgencyStateRepository`, `TriageEventsConsumer` | `RAGServiceTest`, `RAGServiceRn05GateTest`, `GroundedCitationValidatorTest`, `RAGControllerTest`, `RAGControllerApiContractTest`, `FixtureRagPipelineTest`, `SeededCorpusRn05Test` |
| RF-05 Reporte operativo SQL avanzado | PASS | `TriageReportController`/service/repo (según módulo triage), consulta SQL avanzada | `OperationalReportsIntegrationTest`, `TriageReportEvidenceTest`, `docs/demo/local-verification.md` |
| RF-06 Alerta a equipo médico | PASS | Alertas persistidas + SSE: `AlertController`, `AlertChangeListener`, `MedicalAlertRepository`, ADR-15 | `AlertChangeListenerNotificationTest`, integración SLA/alertas, `docs/demo/local-verification.md` SSE/ack |

## 3. Reglas de negocio

| ID | Estado | Evidencia | Pruebas |
|---|---|---|---|
| RN-01 Clasificación por prioridad/bordes | PASS | `TriageClassifier`, `TriageLevel`, `docs/requirements.md` RN-01 | `TriageClassifierTest` E1-E6, boundaries implícitos en casos |
| RN-02 SLA por nivel configurable | PASS | `SlaProperties`, `application.yaml`, `docker-compose.yml` env SLA | `TriageSLAEscalationIntegrationTest`, `SlaProperties`/config tests |
| RN-03 Escalamiento idempotente/condicional | PASS | `SlaEvaluator` usa actualización condicional/transacción; no escala EN_CONSULTA/ALTA | `TriageSLAEscalationIntegrationTest` idempotencia, concurrencia, estados no escalables |
| RN-04 Auditoría clínica inmutable por API | PASS | `AuditEntry*`, escrituras en registro/transición/escalamiento; sin endpoints delete/update auditoría | Tests de integración/transición/SLA verifican filas de auditoría |
| RN-05 Anti-alucinación RAG con citas | PASS | `RAGService`, `GroundedCitationValidator`, `CitationValidator`, fixed no-evidence notice | `RAGServiceRn05GateTest`, `GroundedCitationValidatorTest`, `RAGControllerApiContractTest`, `SeededCorpusRn05Test` |

## 4. Validaciones

| ID | Estado | Evidencia | Pruebas |
|---|---|---|---|
| VAL-01..VAL-11 (rangos, cross-field, campos requeridos) | PASS | DTOs/validación en `application/dto` y controller advice; `spring-boot-starter-validation` | `TriageControllerTest.invalidRangeReturnsNfr20ErrorBody`, tests de validación adicionales en módulo triage |

## 5. Requisitos técnicos

| Requisito | Estado | Evidencia | Nota |
|---|---|---|---|
| >=2 microservicios reactivos Java | PASS | `triage-service/`, `clinical-assistant-service/` | WebFlux en ambos. |
| Spring Boot 3 / WebFlux / Mono-Flux / R2DBC PostgreSQL | PASS | `build.gradle.kts` de ambos servicios; R2DBC + Postgres; uso reactivo en servicios/controladores | Boot 3.5.16, Java 21. |
| Sin llamadas bloqueantes en event loop | PARCIAL | BlockHound en tests/build (`blockhound-junit-platform`), adaptadores JDBC aislados en assistant (ADR-04) | Evidencia de configuración/tests BlockHound presente; la no-existencia total de bloqueos depende de ejecución completa. |
| Kafka `triaje.eventos` con 4 eventos | PASS | `EventType`, `PacienteRegistrado/Escalado/EnConsulta/Alta`, outbox publisher, consumer assistant, compose topic `triaje.eventos` | `EventEnvelopeValidationTest`, `TriageEventSerializationTest`, integración outbox. |
| Resilience4j CB en LLM, fallback 503, triage independiente | PASS | `ResilientLLMRouter`, `LlmCallMetrics`, `Resilience4jConfiguration`, `RAGExceptionHandler`/controller advice 503 `LLM_UNAVAILABLE`; sin llamada síncrona triage->assistant | `Resilience4jConfigurationTest`, `RAGControllerApiContractTest` mapea fallo upstream/503 según contrato. |
| RAG con Spring AI/LangChain4j + vector store documentado | PASS | Spring AI pgvector deps, `PgvectorVectorStoreAdapter`, migraciones `vector`, ADR-04/ADR-05 | Vector store: pgvector. |
| Observabilidad: health/readiness, logs correlación, métricas mínimas; OTel bonus | PASS | Actuator, Prometheus, Micrometer, OTel exporter, Grafana/Jaeger en compose; métricas `triage.classification.total`, `triage.escalations.total`, `assistant.llm.latency` | `docs/demo/local-verification.md` NFR-07/NFR-16. |
| Seguridad: sin credenciales hardcodeadas, auth API key/JWT documentada, validación input | PASS | `ApiKeyAuthenticationFilter` en ambos servicios, keys por env vars, ADR-07; validación DTOs | `README.md`, `docker-compose.yml` usa env/defaults demo locales. |
| Tests: unit+integration, LLM mockeable, Testcontainers; casos requeridos | PASS | JUnit5, StepVerifier, Mockito, Testcontainers Kafka/Postgres/pgvector, fixtures RAG | E1-E6: `TriageClassifierTest`; transiciones: `PatientLifecycleServiceTest`; escalamiento idempotente: `TriageSLAEscalationIntegrationTest`; RAG citas/no-match/no-hallucination: `RAGServiceRn05GateTest`, `GroundedCitationValidatorTest`, `SeededCorpusRn05Test`. |
| docker-compose un comando | PASS | `docker-compose.yml`, `README.md` | Incluye build de apps e infraestructura. |
| Kubernetes manifiestos target Azure | PASS | `infra/k8s/*`, `*/deployment/k8s/*`, ADR-11/ADR-12 | — |
| ADR mínimo 2 | PASS | `docs/adr/README.md` | — |

## 6. Gaps conocidos / acciones recomendadas

| Gap | Impacto | Acción recomendada |
|---|---|---|
| URL Azure viva depende de entorno cost-controlled | Puede incumplirse si el entorno se apaga antes de evaluación | Mantener URL activa durante ventana de evaluación, compartir `X-API-Key` por canal seguro, y adjuntar evidencia/runbook por si requiere reproducción. |
| Evidencia histórica Azure con workarounds en ejecución original | Riesgo de percepción de reproducibilidad incompleta | Referenciar ADR-11 gates y docs de despliegue; aclarar que manifiestos/IaC actuales incorporan correcciones. |
| README antes no hacía visible el diagrama/decisiones | Fricción de evaluación | Corregido en este PR. |

## 7. Referencias rápidas

- `README.md`
- `docs/architecture/diagrams.md`
- `docs/requirements.md`
- `docs/adr/README.md`
- `docs/demo/local-verification.md`
- `docs/operations/despliegue-azure-y-pruebas.md`
- `docker-compose.yml`
- `infra/k8s/`, `infra/azure/`
