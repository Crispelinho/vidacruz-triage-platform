# Auditoría de cumplimiento — Prueba Técnica Dev. WebFlux

- **Primera versión:** 2026-11-27, commit `68f2398` (punta de `develop`)
- **Actualización:** 2026-11-28, commit `e57f6fe` (punta de `develop`, después de los PR #120, #173, #182, #189, #191, #192, #194, #198 y #199)
- **Alcance:** cumplimiento de cada requisito del enunciado (RF, RN, validaciones, datos de referencia, requisitos técnicos y entregables). La auditoría general de arquitectura y calidad se hace por separado.
- **Método:** lectura del código fuente, de las migraciones y de los manifiestos, sin confiar en lo que dicen los ADR o el README. Se usaron `gh` (CI, ramas, visibilidad, runs de despliegue) y `curl` contra la URL de Azure. Cuando un punto no se volvió a comprobar en la actualización, se indica.
- **Escala:** CUMPLE / PARCIAL / NO CUMPLE / NO VERIFICABLE.

> Rutas abreviadas: `T/` = `triage-service/triage-service/src/main/java/com/vidacruz/triageservice/`, `A/` = `clinical-assistant-service/clinical-assistant-service/src/main/java/com/vidacruz/clinical/`.

## Resumen ejecutivo

La implementación funcional es sólida: RN-01, RN-03, RF-05 (SQL avanzado de verdad) y RN-05 (validación de citas contra el corpus y contra el conjunto recuperado, más el `GroundingCheck` del texto) están bien resueltos.

**Las tres brechas críticas del 2026-11-27 están cerradas:**

1. **`main` ya tiene la solución.** `main` es la rama por defecto del repo público, está en `38d9d77` y contiene los dos servicios, la infraestructura, los ADR y los scripts. Queda una tarea: `main` va **77 commits por detrás** de `develop`, así que hay que promover `develop` otra vez antes de la entrega.
2. **La URL de Azure vuelve a responder.** La verificación R-06 sobre `206ede7` dio 13/13 PASS, incluidos RF-03, RF-06 por SSE y 5/5 respuestas RAG con proveedor real (`docs/audits/20261128/azure-develop-206ede7-r6.txt`). El run `46163138392` de `deploy.yml` sobre `e57f6fe` terminó en *success* el 2026-11-28 a las 16:03Z, y `GET /ops/triage/health` devolvió 200 a las 17:04Z.
3. **Spring AI se usa de verdad en el chat.** `SpringAIChatAdapter` y `SpringAIChatConfiguration` (PR #182) enrutan los nodos Gemini y OpenCode por `ChatClient`. El *retrieval* sigue fuera de Spring AI: el embedding es léxico y la autoconfiguración de pgvector sigue excluida. Ver T-08.

Lo que queda pendiente es sobre todo de entrega y de higiene: promover `develop` a `main`, mejorar la calidad semántica del *retrieval*, cerrar los gates formales de los ADR y hacer limpieza menor.

## 1. Matriz de cumplimiento

### 1.1 Requerimientos funcionales

| ID | Verdict | Evidencia | Observaciones |
|---|---|---|---|
| RF-01 Registro y clasificación | **CUMPLE** | `T/application/controller/TriageController.java` (201); `T/domain/service/TriageRegistrationService.java` (clasifica, persiste, audita y escribe el outbox en una transacción); la respuesta incluye `id`, `nivel`, `slaMinutos`, `estado: REGISTRADO` y `fechaRegistro`; 400 en `ApiExceptionHandler.java:30` | Publica `paciente_registrado` mediante el outbox transaccional (`OutboxPublisher`). Además soporta `Idempotency-Key` (no se pedía). |
| RF-02 Flujo de estados | **CUMPLE** | `T/domain/service/PatientLifecycleService.java` (matriz de transiciones, 409, UPDATE condicional + auditoría + evento + resolución de la alerta en una transacción); el GET devuelve `escalado` y los minutos en el estado actual (`estado_desde` en V6) | Un `estado` desconocido (p. ej. `"FOO"`) devuelve 409 en lugar de 400 (no se volvió a comprobar el 2026-11-28). Es defendible, pero no es lo esperado. |
| RF-03 SLA y escalamiento | **CUMPLE** | `T/domain/service/SlaEvaluator.java`; `T/infrastructure/config/SlaEvaluationScheduler.java` (`fixedDelayString` configurable); `SlaProperties` y `app.sla.defaults.*`; activo en compose y en K8s (`triage-service/deployment/k8s/configmap.yaml`). **Verificado en Azure**: `PASS RF-03 SLA escalation on Azure` (R-06, 2026-11-28) | En `application.yaml` el scheduler viene **desactivado por defecto** (`evaluation-enabled: false`); solo se activa por variable de entorno. Hay que documentarlo. |
| RF-04 A — RAG con citas | **CUMPLE** (con observación) | `A/domain/services/RAGService.java` (embed → top-K → umbral → LLM → parser JSON estricto → `CitationValidator` → `GroundingCheck`); `POST /asistente/consulta`; chat por Spring AI `ChatClient` en Gemini y OpenCode. **Verificado en Azure**: 5/5 respuestas RAG con proveedor real (PROTO-001) | El framework ya no es una brecha (ver T-08). Queda una observación de calidad: el *embedding* sigue siendo léxico por defecto (`EMBEDDING_PROVIDER:lexical`, `LexicalEmbeddingAdapter`), así que una paráfrasis sin raíces compartidas no recupera nada. Existe `GeminiEmbeddingAdapter`, pero activarlo exige reingestar el corpus. |
| RF-04 A — "sin protocolo" | **CUMPLE** | `RAGService.java`: si no hay resultados por encima del umbral, devuelve `RAGResponse.noEvidence()` **sin llamar al LLM** (`"no existe protocolo disponible"`, `fuentes: []`) | Una cita inválida o un JSON inválido del LLM también devuelven el aviso fijo (`RAGExceptionHandler`). |
| RF-04 B — Estado de urgencias | **CUMPLE** | `A/infrastructure/adapters/TriageEventsConsumer.java` (reactor-kafka, ack después de proyectar, reconstrucción desde el offset 0 si la proyección está vacía); `redis/apply-patient-state.lua` (aplicación atómica, deduplicada por secuencia por paciente, ALTA sale de los sets activos) | No consulta la API de triage. Persiste en Redis (ADR-08). |
| RF-05 Reporte de operación | **CUMPLE** | `T/infrastructure/report/OperationReportSql.java`: `generate_series`, `ROW_NUMBER() OVER (PARTITION BY …)`, `COUNT(*) FILTER`, `PERCENTILE_CONT(0.95) WITHIN GROUP` y `GROUPING SETS`; el adaptador solo mapea filas | No hay agregación en memoria. `hasta` se toma como **fecha natural inclusiva** (`ReportPeriod.java`). Los endpoints *legacy* `/triage/reports/*` (`ReportController.java:17`) siguen existiendo. |
| RF-06 Alertas al equipo médico | **CUMPLE** | Alerta persistida en `medical_alert` dentro de la transacción de escalamiento, con `UNIQUE (patient_id)`; SSE en `GET /triage/alertas/stream` mediante `pg_notify`/LISTEN; reconocimiento en `POST /triage/alertas/{id}/reconocimiento`; ADR-15. **Verificado en Azure** (R-06 sobre `206ede7`): snapshot + heartbeat, alerta en vivo + resuelta, reconocimiento 200, reconocimiento inválido 400 (`TRG-004`), duplicado 409 (`TRG-009`) | En local, con el *harness* de K8s, la primera pasada justo después de reiniciar los pods dio 3 fallos intermitentes de SSE; la segunda dio 33/33. El *harness* canónico (PR #198) dio 33/33 (`local-canonical-harness-e2e.txt`). |

### 1.2 Reglas de negocio

| ID | Verdict | Evidencia | Observaciones |
|---|---|---|---|
| RN-01 Clasificación | **CUMPLE** | `T/domain/service/TriageClassifier.java`: evalúa de mayor a menor gravedad y gana el primer nivel que coincide; los límites coinciden con la tabla (`<40`, `>120`, `90–93`, `90–100`, `≥38.5`, `9–13`, `90–120`, `38.0–<38.5`, `≥7`) | — |
| RN-01 Nivel 5 (quejas administrativas) | **CUMPLE** | `TriageClassifier.java`: normaliza NFD, quita tildes, pasa a minúsculas y colapsa espacios; "Renovación de recetario" → NO_URGENTE_2 | Es coincidencia **exacta** tras normalizar: "renovación de recetario médico" o "vacuna" se quedan en NO_URGENTE. Es una interpretación válida, pero conviene documentarla como supuesto. |
| RN-02 SLA por nivel | **CUMPLE** | `TriageLevel.java` (5/10/30/60/120) y `SlaProperties.minutesFor` con override; el `configmap.yaml` usa los valores del enunciado | En compose se usan SLA cortos de demo a propósito. |
| RN-03 Idempotencia del escalamiento | **CUMPLE** | Candidatos `estado='REGISTRADO' AND escalado=FALSE`; `UPDATE … WHERE escalado = FALSE AND estado = 'REGISTRADO'`; auditoría, evento y alerta solo si `updated == 1`; `medical_alert.patient_id UNIQUE` como respaldo transaccional | `SlaEvaluator.java:85` sigue aplicando `defaultIfEmpty(1)`: la garantía en una carrera perdida queda en la restricción `UNIQUE` de la BD y no en el código. Tests: `SlaEvaluatorTest`. |
| RN-04 Auditoría clínica | **CUMPLE** | `auditoria_clinica` (V1) con timestamp, `paciente_id`, estado anterior, estado nuevo y tipo; se escribe en REGISTRO, ENTRADA_CONSULTA, ESCALAMIENTO y ALTA; ningún controlador declara `@DeleteMapping` | La protección es a nivel de API, que es lo que pide el enunciado. La verificación de filas en el PostgreSQL de Azure está en `azure-develop-206ede7-db-rowlevel.txt` (PR #187). |
| RN-05 Anti-alucinación | **CUMPLE** | `A/domain/services/CitationValidator.java`: cada `(protocolo, seccion)` debe existir en el `CorpusIndex` **y** en el conjunto recuperado por encima del umbral; los marcadores `[n]` se validan; cero citas → inválida → aviso fijo. Además `A/domain/services/GroundingCheck.java` comprueba el texto contra la evidencia, y `ec0578e` lo mantiene activo también en las respuestas marcadas | El riesgo residual del 2026-11-27 (validar solo la cita y no el texto) queda **mitigado** por `GroundingCheck`. Sigue siendo una heurística, no una prueba de fidelidad. |

### 1.3 Validaciones de datos (RF-01)

| Campo | Verdict | Evidencia |
|---|---|---|
| FC 20–250, PAS 60–260, PAD 30–160, SpO2 70–100, T 34.0–42.0, dolor 0–10, GCS 3–15, edad 0–120, nombre 3–100 | **CUMPLE** | `T/domain/model/PatientRegistration.java`; los mensajes nombran el campo en español (`FieldError`) |
| PAD < PAS | **CUMPLE** | `PatientRegistration.java` (`diastolic >= systolic` → 400 en `presion_diastolica`) |
| Campo ausente | **CUMPLE** | `PatientRegistrationRequest.java` (`no puede estar vacío`) |
| JSON mal formado o tipo incorrecto | **NO VERIFICABLE** | Sigue sin haber `@ExceptionHandler` para `ServerWebInputException` en `ApiExceptionHandler.java`. Lo responde el 400 por defecto de Spring, que no garantiza el formato `{code, message, fields}` ni nombrar el campo. |

### 1.4 Pacientes de ejemplo E1–E6

| Caso | Nivel esperado | Regla que dispara | Verdict | Evidencia |
|---|---|---|---|---|
| E1 | CRITICO | FC 130 > 120 | **CUMPLE** | `TriageClassifierTest.java` |
| E2 | URGENTE | SpO2 91 (gana a FC 95 de nivel 3) | **CUMPLE** | idem |
| E3 | URGENTE_2 | T 38.2 | **CUMPLE** | idem |
| E4 | NO_URGENTE | ninguna | **CUMPLE** | idem |
| E5 | NO_URGENTE_2 | nivel 4 + "Renovación de recetario" | **CUMPLE** | idem |
| E6 | CRITICO | FC 35, SpO2 85, PAS 80, GCS 6 | **CUMPLE** | idem |

### 1.5 Consultas de ejemplo del asistente

| Consulta | Verdict | Evidencia |
|---|---|---|
| 1. Crisis hipertensiva → PROTO-001 | **CUMPLE** (CI + Azure) | `integration/SeededCorpusRn05Test.q1HypertensionRetrievesProto001AndCitesIt`; Azure 5/5 con proveedor real |
| 2. Primer antibiótico en sepsis → PROTO-002 | **CUMPLE** (CI) | `SeededCorpusRn05Test.q2SepsisAntibioticRetrievesProto002AndCitesIt` |
| 3. Diabetes tipo 2 → aviso sin contenido | **CUMPLE** (CI) | `SeededCorpusRn05Test.q3DiabetesReturnsTheFixedNoticeWithoutCallingTheLlm` |

> Estos tests usan `@Testcontainers(disabledWithoutDocker = true)`: **se saltan en silencio** donde no hay Docker. En CI (ubuntu) sí corren.

### 1.6 Requerimientos técnicos

| ID | Requisito | Verdict | Evidencia / observaciones |
|---|---|---|---|
| T-01 | ≥ 2 microservicios reactivos | **CUMPLE** | `triage-service` y `clinical-assistant-service`, ambos WebFlux; ADR-01 |
| T-02 | Boot 3 + WebFlux + Mono/Flux | **CUMPLE** | Boot 3.5.16; controladores que devuelven `Mono`/`Flux`. ADR-10 (*Accepted* para el alcance de evaluación) advierte del EOL de 3.5 |
| T-03 | R2DBC para PostgreSQL | **PARCIAL** | Triage es 100 % R2DBC. El asistente usa **JDBC** (`JdbcClient`, `VectorRepository`) aislado en un `Scheduler` acotado (`PgvectorVectorStoreAdapter`); está justificado en ADR-04, pero es una desviación explícita del "driver reactivo para PostgreSQL" |
| T-04 | Sin llamadas bloqueantes en el event loop | **CUMPLE** | `KafkaTemplate.send` en `boundedElastic`; JDBC en su scheduler; el nodo Gemini de Spring AI se suscribe en `Schedulers.boundedElastic()` (`RAGConfiguration.java:131`); BlockHound con control negativo. El único `block()` está en el `ApplicationRunner` de arranque `corpusIndexRunner`, fuera del event loop |
| T-05 | Kafka `triaje.eventos` con 4 eventos | **CUMPLE** | `paciente_registrado`, `paciente_en_consulta`, `paciente_alta` y `paciente_escalado`; topic en compose y en K8s; key = `pacienteId`. En Azure, el consumidor lleva el `traceparent` (R-06) |
| T-06 | Formato documentado | **CUMPLE** | JSON con sobre aplanado en `OutboxEventWriter.java`; ADR-02/ADR-03 y tests `EventEnvelopeValidationTest`/`TriageEventSerializationTest` |
| T-07 | Resilience4j CB + fallback 503 | **CUMPLE** | `ResilientLLMRouter.java` (CB por nodo + timeout + failover); `RAGExceptionHandler` → 503 `LLM_UNAVAILABLE`; `ResilientLLMRouterTest`, `Resilience4jConfigurationTest`. Comprobado también en el E2E local con el LLM caído |
| T-08 | RAG con Spring AI o LangChain4j | **CUMPLE** (chat) / **PARCIAL** (*retrieval*) | Antes: NO CUMPLE. Ahora `SpringAIChatAdapter` y `SpringAIChatConfiguration` importan `org.springframework.ai` y los nodos `gemini` y `opencode` van por `ChatClient` (`RAGConfiguration.java:130-138`). Siguen fuera de Spring AI: el nodo Ollama y el opcional OpenRouter (adaptadores `WebClient`), el embedding (léxico propio) y el vector store (`PgVectorStoreAutoConfiguration` sigue excluida en `application.yaml:18`). ADR-05 sigue en *Proposed* |
| T-09 | Vector store documentado en ADR | **CUMPLE** | pgvector, ADR-04 |
| T-10 | Health/readiness | **CUMPLE** | `management.endpoint.health.probes.enabled: true`; probes `startup`/`liveness`/`readiness` en ambos `deployment.yaml`; en Azure, health de los dos servicios vía `/ops` → 200 |
| T-11 | Logs estructurados con correlación | **CUMPLE** | Antes: PARCIAL. PR #120: `logging.structured.format.console: ${LOG_FORMAT:ecs}` en los dos servicios (`application.yaml:24` triage, `:151` asistente); `CorrelationIdWebFilter` + `CorrelationIdMdcConfiguration` llevan `X-Correlation-Id` al MDC mediante `ContextRegistry` y `spring.reactor.context-propagation: auto`; el valor de entrada se restringe a `[A-Za-z0-9._-]{1,128}` |
| T-12 | Métricas Micrometer | **CUMPLE** | `triage.patients.classified` (por nivel), `triage.patients.escalated`, `assistant.llm.latency` (`Timer`), más `assistant.answers` y `assistant.urgency.events`; Prometheus y Grafana en compose |
| T-13 | OpenTelemetry (bonus) | **CUMPLE** | `micrometer-tracing-bridge-otel` + OTLP; `traceparent` propagado por el outbox (V5) y el consumidor; Jaeger en compose. En Azure no hay Jaeger: la traza compartida solo tiene evidencia local |
| T-14 | Sin credenciales hardcodeadas; key LLM por env | **CUMPLE** (con observación) | Antes: PARCIAL. PR #120 quitó los *fallbacks* `test-*-key`: con la API key vacía, `ApiKeyRegistry.create` lanza una excepción y el servicio no arranca (`SecurityConfiguration.java:32-41`, `ApiKeyRequiredTest`). El dueño declaró rotadas todas las keys el 2026-11-28 (`docs/audits/20261128/secret-hygiene-rotation.md`); ese registro sigue en **PARTIAL** hasta hacer el escaneo de repo, imágenes e historial. Además, las preguntas se redactan (PII) antes de salir hacia Gemini, OpenCode y OpenRouter (`PiiRedactingLLMProvider`, PR #189) |
| T-15 | Autenticación (API key/JWT) documentada | **CUMPLE** | `ApiKeyAuthenticationFilter` (comparación en tiempo constante), autorización por *scope* por ruta y `denyAll` por defecto (`SecurityConfiguration.java:58-67`); ADR-07 |
| T-16 | Validación de entradas | **CUMPLE** | §1.3; `InvalidPromptException` → 400 en el asistente |
| T-17 | Tests unitarios + integración, LLM mockeable | **CUMPLE** | El LLM se mockea por puerto (`NamedLLMProvider`/`LLMRouter`) y por stub HTTP (`*WireTest`); Testcontainers para PostgreSQL, Kafka y pgvector. Suite del asistente en local (copia en ruta ASCII): 312 tests, 0 fallos, 2 omitidos |
| T-18 | Cobertura mínima: E1–E6, transiciones, idempotencia, citas, sin coincidencia | **CUMPLE** | `TriageClassifierTest`, `PatientLifecycleServiceTest`, `SlaEvaluatorTest` + `TriageSLAEscalationIntegrationTest`, `CitationValidatorTest`/`GroundedCitationValidatorTest`, `GroundingCheckTest`, `RAGServiceRn05GateTest`, `SeededCorpusRn05Test` |
| T-19 | docker-compose con un solo comando | **CUMPLE** (estático) | `docker compose up -d --build` levanta PostgreSQL ×2, Kafka, Redis, los dos servicios y la observabilidad; `docker compose config -q` es válido. No se levantó en esta auditoría |
| T-20 | K8s (Deployment, Service, probes) para Azure | **CUMPLE** | `*/deployment/k8s/*`, `infra/k8s/*`, `infra/azure/main.bicep` (AKS), `.github/workflows/deploy.yml`. Además se validó en local con Docker Desktop K8s: 33/33 checks |
| T-21 | ≥ 2 ADR | **CUMPLE** (con ruido) | Ya existe un índice, `docs/adr/README.md`, que explica que *Proposed* significa "decidido e implementado, con gates de verificación abiertos" (regla D-ADR-FORMAL) y enlaza el acta `adr-acceptance-acta-20261127.md`. Siguen existiendo ficheros con el mismo número (ADR-05 ×2, uno de ellos *Superseded*; ADR-06 ×2; ADR-07 ×2; ADR-09 ×5; ADR-12 ×2) |

### 1.7 Entregables obligatorios

| # | Entregable | Verdict | Evidencia |
|---|---|---|---|
| E-1 | Repositorio GitHub público con el código completo | **CUMPLE** (con tarea pendiente) | Antes: NO CUMPLE. El repo es `PUBLIC`, la rama por defecto es `main` y `main` (`38d9d77`) ya contiene los dos servicios, `infra`, `k8s`, `docs`, `scripts`, `postman` y `observability`. **Pendiente:** `main` va 77 commits por detrás de `develop` (`e57f6fe`): faltan, entre otros, Spring AI en el chat (#182), la redacción de PII en OpenRouter (#189) y la evidencia de Azure (#192) |
| E-2 | README con ejecución en un comando, arquitectura **con diagrama**, tecnologías y decisiones | **CUMPLE** | Antes: PARCIAL. PR #120: diagrama Mermaid en el README (`README.md:187`), tablas de tecnologías y de decisiones clave con enlace a cada ADR, y la línea de idempotencia corregida (`README.md:164`) |
| E-3 | Tests automatizados de los flujos | **CUMPLE** | CI verde en `develop`: `clinical-assistant-service` en `9b3ba90` y `deploy` en `e57f6fe` (2026-11-28). En local, las suites solo corren desde una ruta ASCII (ver §4) |
| E-4 | URL desplegada en Azure + manifests/IaC | **CUMPLE** | Antes: NO CUMPLE (URL). `https://vidacruz-l7rsr6.northcentralus.cloudapp.azure.com` resuelve y responde; `GET /ops/triage/health` → 200 (2026-11-28 17:04Z). Verificación R-06 sobre `206ede7`: 13/13 PASS. El run `46163138392` de `deploy.yml` sobre `e57f6fe` terminó en *success*. La R-06 **no** se ha repetido sobre `e57f6fe` |
| E-5 | ADR (mínimo 2) | **CUMPLE** | Ver T-21 |
| — | Historial de commits iterativo | **CUMPLE** | Un PR por cambio y commits convencionales, del 2026-11-24 al 2026-11-28 |

## 2. Brechas ordenadas por impacto en la evaluación

| # | Severidad | Brecha | Estado al 2026-11-28 |
|---|---|---|---|
| 1 | ~~Crítica~~ | `main` solo tenía el scaffold | **Cerrada.** `main` tiene la solución. Queda promover `develop` (77 commits por delante) antes de la entrega |
| 2 | ~~Crítica~~ | La URL de Azure no resolvía | **Cerrada.** La URL responde, R-06 13/13 sobre `206ede7` y despliegue de `e57f6fe` en *success*. Conviene repetir la R-06 sobre el commit final |
| 3 | ~~Alta~~ → **Media** | Spring AI declarado y sin usar; embedding léxico | **Cerrada en el chat** (#182). Queda abierta en el *retrieval*: embedding léxico por defecto y vector store sin Spring AI. Afecta a la calidad semántica del RAG |
| 4 | ~~Media~~ | README sin diagrama | **Cerrada** (#120) |
| 5 | ~~Media~~ | Logs no estructurados; `correlationId` fuera del MDC | **Cerrada** (#120) |
| 6 | **Media** | ADR con números duplicados y estados *Proposed* | **Mitigada.** El índice explica qué significa *Proposed* y enlaza el acta de gates. Siguen los números duplicados, y ADR-05 y ADR-11 siguen en *Proposed* |
| 7 | ~~Media~~ → **Baja** | Keys expuestas y *fallbacks* `test-*-key` | **Mitigada.** Ya no hay *fallbacks* y el servicio falla al arrancar sin key; el dueño declaró la rotación. Falta el escaneo de repo, imágenes e historial |
| 8 | **Baja** | El asistente usa JDBC (no R2DBC) para pgvector | Sin cambios. Justificado en ADR-04 |
| 9 | ~~Baja~~ | RF-06 E2E en Azure sin verificar | **Cerrada** (R-06, 2026-11-28) |
| 10 | **Baja** | Árbol *stale* `triage-service/src/` (8 ficheros) junto a `triage-service/triage-service/`; endpoints *legacy* `/triage/reports/*` | Sin cambios. La línea de idempotencia del README sí se corrigió |
| 11 | **Baja** | `estado` desconocido → 409; JSON mal formado sin handler propio | Sin cambios (el handler sigue sin existir; el 409 no se volvió a comprobar) |
| 12 | **Baja** | `defaultIfEmpty(1)` en el escalamiento delega la idempotencia en la carrera a `UNIQUE(patient_id)` | Sin cambios (`SlaEvaluator.java:85`) |

## 3. Quick wins pendientes

1. **Promover `develop` a `main`** mediante PR antes de la entrega, para que el evaluador vea Spring AI, la redacción de PII y la evidencia de Azure.
2. **Repetir la verificación R-06 en Azure** sobre el commit que se entregue (hoy la evidencia es de `206ede7`) y guardarla en `docs/audits/`.
3. **Retrieval semántico**: valorar activar `GeminiEmbeddingAdapter` o un `EmbeddingModel` de Spring AI con reingesta del corpus, o explicar en el README por qué se mantiene el léxico (corpus pequeño, determinismo, coste cero).
4. **ADR**: renumerar los ficheros duplicados o marcarlos como anexos en el índice, y cerrar los gates de ADR-05 y ADR-11.
5. **Higiene de seguridad**: hacer el escaneo de repo, imágenes e historial para pasar `secret-hygiene-rotation.md` de PARTIAL a PASS.
6. **Limpieza**: quitar el árbol `triage-service/src/` *stale*, decidir qué hacer con `/triage/reports/*` y añadir un `@ExceptionHandler(ServerWebInputException.class)` que devuelva `{code, message, fields}`.

## 4. Lo que no se pudo verificar en esta auditoría

- **Tests en local desde la ruta original**: fallan con `ClassNotFoundException` del *test executor* de Gradle en Windows por la `é` de `Pruebas técnicas`. Desde una copia en ruta ASCII (`C:\tmp\…`), la suite del asistente pasa (312 tests, 0 fallos, 2 omitidos). La evidencia principal sigue siendo la de CI.
- **`docker compose up` completo**: no se ejecutó; solo se validó la sintaxis. Sí se ejecutó el E2E en Kubernetes local (33/33).
- **Llamada real al LLM en local**: el Ollama del host (llama3.1:8b) no respondió dentro del tiempo límite, así que en local solo se comprobaron el modo degradado (503) y los stubs. La llamada real sí está verificada en Azure (5/5).
- **R-06 sobre `e57f6fe`**: el despliegue terminó en *success* y el health responde, pero la batería funcional completa en Azure solo existe para `206ede7`.
