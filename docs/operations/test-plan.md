# Plan de pruebas — VidaCruz Triage Platform

> Estado: vigente al 2026-11-28. Consolida los planes por servicio y la matriz de cumplimiento en un solo documento ejecutable. ADR-11 y ADR-05 permanecen `Proposed`; este plan registra evidencia, no promueve decisiones.

## 1. Propósito y alcance

Verificar que la plataforma cumple los requisitos funcionales y no funcionales del enunciado, tanto en el entorno local reproducible como en el despliegue público de Azure. Cubre los seis niveles de prueba: unidad, integración, verificación de configuración, E2E con Docker Compose, E2E en Kubernetes local y verificación en vivo sobre Azure (API, Kafka, PostgreSQL y gateway).

Queda fuera de alcance: medición de coste real del proveedor LLM, spike de ejecución completo con llamadas reales, rotación/escaneo de secretos e imágenes, y traza compartida en Jaeger dentro de Azure (no hay Jaeger desplegado allí).

## 2. Documentos de referencia

| Documento | Uso |
|---|---|
| `docs/requirements.md:41-202` | Definición canónica de RF, RN, NFR y VAL |
| `docs/requirements.md:208-232` | Restricciones técnicas TC-01…TC-12 y API-01…API-06 |
| `docs/requirements.md:342-345` | Eventos EVT-01…EVT-04 |
| `docs/requirements.md:389-407` | Casos de aceptación TST-* |
| `docs/requirements.md:415-493` | Ambigüedades AMB-*, decisiones DEC-*, riesgos R-* |
| `docs/services/triage-service.md:400-498` | Plan de pruebas de Triage (E1-E6, VAL, estado, SLA, auditoría, outbox) |
| `docs/services/clinical-assistant-service.md:450-509` | Plan de pruebas del Asistente (unidad, slice, Testcontainers, resiliencia, E2E) |
| `docs/demo/enunciado-compliance-matrix.md:25-69` | Matriz de cumplimiento RF/RN/VAL |
| `docs/audits/20261126/evidence-test-coverage-register-20261126.md` | Distinción IMPLEMENTED / EXECUTED / PASS |
| `docs/audits/20261128/azure-v106-verification.md` | Registro de verificación en Azure y del rerun limpio |
| `docs/demo/local-kubernetes-e2e.md` | Registro del E2E local de 33 comprobaciones |

## 3. Estrategia y niveles

| Nivel | Objetivo | Dónde se ejecuta | Evidencia principal |
|---|---|---|---|
| Unidad | Reglas RN-01/RN-02, validaciones VAL-01…VAL-11, puertos/adaptadores | Gradle en CI | `./gradlew build --no-daemon` (`.github/workflows/ci.yml:12-13`) |
| Integración | Persistencia, outbox, idempotencia, SLA, proyección, rollback, reportes | Gradle + Testcontainers | XML Surefire subido por los workflows de servicio |
| Verificación de configuración | Spike offline, calibración de embeddings, proveedores opt-in | `workflow_dispatch` con clave real | `gemini-embedding-verification.yml:42`, `openrouter-chat-verification.yml:43` |
| E2E Compose | Arranque en un comando, modo degradado, métricas y trazas | `docker compose` local | `docs/demo/local-verification.md` |
| E2E Kubernetes local | 33 comprobaciones de punta a punta, incluido Kafka y Jaeger | Docker Desktop, contexto `docker-desktop` | `docs/audits/20261128/local-e2e-main-final2.txt` |
| Verificación en vivo | API pública, gateway, Newman, R-06, chat real, BD row-level | AKS `vidacruz-aks` | `docs/audits/20261128/azure-*.txt` y `azure-v106-verification.md` |

## 4. Trazabilidad de escenarios

### 4.1 Requisitos funcionales y reglas de negocio

| ID | Escenario | Criterio de aceptación | Cobertura | Evidencia |
|---|---|---|---|---|
| RF-01 | Registro y clasificación | Persiste, audita, publica EVT-01 | Ejecutado local y Azure | E1-E6 en `local-e2e-main-final2.txt`; Newman; `azure-r6-clean-rerun.txt` |
| RF-02 | Ciclo de estados | `REGISTRADO`→`EN_CONSULTA`→`ALTA`; transición inválida 409 | Ejecutado local y Azure | E2E local áreas RF-02; Newman; R-06 |
| RF-03 | SLA y escalado | Un solo escalado por paciente; `PENDIENTE` | Ejecutado local y Azure + fila en BD | R-06; `azure-db-rowlevel-verification.txt:17-32` |
| RF-04 | Consulta RAG y proyección de urgencia | Cita del corpus o aviso fijo | Ejecutado local y Azure | Chat 35/35; Newman; E2E local |
| RF-05 | Informe de operaciones | Conteos por hora, media y p95; excluye sin consulta (AMB-05) | Ejecutado local y Azure | E2E local área reportes; Newman |
| RF-06 | Alerta al equipo médico | SSE con snapshot, latido, alta y resolución | Ejecutado local y Azure + fila en BD | `azure-r6-clean-rerun.txt`; `azure-db-rowlevel-verification.txt:31-32` |
| RN-01 | Reglas de clasificación | E1/E6 `CRITICO`, E2 `URGENTE`, E3 `URGENTE_2`, E4 `NO_URGENTE`, E5 `NO_URGENTE_2` | Ejecutado | `local-e2e-main-final2.txt:7-12` |
| RN-02 | SLA por nivel | 5/10/30/60/120 min configurables | Ejecutado | ConfigMap `triage-service-config`; R-06 `sla=5` |
| RN-03 | Idempotencia de escalado | Una fila de alerta, nunca en `EN_CONSULTA`/`ALTA` | Ejecutado | `azure-db-rowlevel-verification.txt:28-32`; integración SLA |
| RN-04 | Auditoría clínica | Fila por cambio de estado; sin endpoints de borrado/actualización | Ejecutado local y Azure | `azure-db-rowlevel-verification.txt:27-30`; E2E local |
| RN-05 | Anti-alucinación | Cita válida, rechazo de cita inválida, aviso fijo | Ejecutado; reingesta en producción pendiente | `docs/audits/20261127/gemini-embedding-tst-rag-verification.md`; chat negativa 5/5 |

### 4.2 Validaciones, API y eventos

| ID | Escenario | Cobertura | Evidencia |
|---|---|---|---|
| VAL-01…VAL-11 | Rangos de signos, edad, nombre y queja | Ejecutado en unidad/integración | `docs/services/triage-service.md:400-498`; suites Gradle |
| API-01…API-06 | Seis endpoints públicos | Ejecutado local y Azure | Newman 31 peticiones / 70 aserciones; 16/16 URLs del smoke |
| EVT-01…EVT-04 | `paciente_registrado`, `paciente_escalado`, `paciente_en_consulta`, `paciente_alta` | Ejecutado local y Azure | `azure-db-rowlevel-verification.txt:19-22`; Kafka en `local-e2e-main-final2.txt` |
| RF-16 | Registro con `traceparent` explícito | Ejecutado | Etiqueta del harness, no un ID canónico de `requirements.md` |

### 4.3 Requisitos no funcionales

| ID | Escenario | Cobertura | Evidencia / observación |
|---|---|---|---|
| NFR-01 | Caminos no bloqueantes | Ejecutado | BlockHound/TST-RXN en la suite Gradle |
| NFR-02 | Kafka con formato documentado y consumidores idempotentes | Ejecutado | Outbox→Kafka en E2E local y R-06 |
| NFR-03 | Circuit breaker del LLM y `503` | Local ejecutado; Azure no inyectado | Camino degradado local tarda ~97 s |
| NFR-04 | Triage independiente del Asistente | Local ejecutado | E2E local, caso degradado |
| NFR-05 | Health, readiness y liveness | Ejecutado | Smoke 16/16 incluye `/ops/.../health/readiness` |
| NFR-06 | Logs estructurados con correlation id | Ejecutado | Reportes E2E y R-06 |
| NFR-07 | Métricas Micrometer | Ejecutado | E2E local área métricas |
| NFR-08 | SLA e intervalo configurables | Ejecutado | ConfigMap y pruebas de integración |
| NFR-09 | Sin credenciales embebidas | Parcial | Inyección por Secret verificada; rotación/escaneo abierto (ADR-11) |
| NFR-10 | Autenticación por API key | Ejecutado | Negativas 401 en E2E local y Newman |
| NFR-11 | Validación de entrada | Ejecutado | VAL-01…VAL-11 en unidad/integración |
| NFR-12 | Arranque con un comando | Ejecutado | `docs/demo/local-verification.md` |
| NFR-13 | Manifiestos Kubernetes con probes, ConfigMap y Secret | Ejecutado | Workflow `k8s-manifests.yml` |
| NFR-14 | Destino Azure AKS | Ejecutado | Rerun limpio de `provision.ps1` + `deploy.yml` |
| NFR-15 | LLM mocable en pruebas | Ejecutado | Suites unitarias del Asistente |
| NFR-16 | Trazas OpenTelemetry | Local completo; Azure parcial | Jaeger local con spans de ambos servicios; en Azure solo `traceparent` outbox→Kafka |
| NFR-17 | Testcontainers para PostgreSQL y Kafka | Ejecutado | Integraciones Gradle |
| NFR-18 | Commits progresivos | Ejecutado | Historial del repositorio |
| NFR-19 | Etiquetas de baja cardinalidad | Ejecutado por revisión | Revisión de métricas y NFR-07 |
| NFR-20 | Cuerpo de error consistente | Ejecutado | `TRG-004`, `TRG-009`, `UNAUTHORIZED` en Newman y E2E |

### 4.4 Casos de aceptación

| ID | Escenario | Cobertura | Evidencia |
|---|---|---|---|
| TST-CLS-01…06 | E1…E6 → nivel esperado | Ejecutado | `local-e2e-main-final2.txt:7-12` |
| TST-STA | Transiciones válidas e inválidas | Ejecutado | E2E local RF-02 |
| TST-ESC | Escalado único, concurrente y sin estado elegible | Ejecutado | Integración SLA + R-06 |
| TST-AUD | Filas de auditoría, sin borrado/actualización | Ejecutado | `azure-db-rowlevel-verification.txt:27-30` |
| TST-RAG | Tres preguntas oficiales con LLM simulado | Ejecutado | `gemini-embedding-tst-rag-verification.md` |
| TST-RES | Circuit breaker abierto, `503 LLM_UNAVAILABLE`, Triage sigue sirviendo | Local ejecutado | E2E local caso degradado; Azure no inyectado |
| TST-IDM | Replays, clave duplicada con cuerpo distinto, concurrencia | Ejecutado | `TriageIdempotencyEvidenceTest` |
| TST-PRJ | Reconstrucción desde eventos, duplicados y desorden | Ejecutado | `SeededCorpusRn05Test`, integración de proyección |
| TST-RPT | Conteos, media y p95 vs fixtures | Ejecutado | `OperationalReportsIntegrationTest`, E2E local |
| TST-RXN | Ausencia de llamadas bloqueantes | Ejecutado | BlockHound en la suite Gradle |

### 4.5 Riesgos

| ID | Escenario | Estado de la mitigación |
|---|---|---|
| R-01 | Intervalo FC 40-89 sin regla | Documentado como ambigüedad; sin escenario de aceptación adicional |
| R-02 | Fin de soporte de Spring Boot 3.5 | Registrado; no bloqueante para la entrega |
| R-03 | pgvector JDBC bloqueante en camino reactivo | Control negativo BlockHound PASS; recuperación pgvector real pendiente |
| R-04 | Latencia y coste del LLM | Coste NO medido; compuerta abierta en ADR-05 |
| R-05 | Deriva de versión de Spring AI | PR de migración #182 en conflicto; excluido del release |
| R-06 | Acceso/cuota de Azure al entregar | Cubierto: despliegue público verificado y rerun limpio |
| R-07 | Doble escritura BD + Kafka | Outbox verificado local y en Azure |
| R-08 | Mensajes duplicados/desordenados | Cubierto por TST-IDM y TST-PRJ |
| R-09 | Pacientes duplicados por reintento del cliente | Cubierto por TST-IDM |
| R-10 | Fallo anti-alucinación | Aviso fijo verificado 5/5; reingesta en producción pendiente |

## 5. Datos de prueba

- Fixtures E1…E6 y rangos inválidos: `docs/services/triage-service.md:402-415`, normalización sin acentos en `:437`. El corpus y el clasificador son **españoles**; la fixture E5 es `Renovacion de recetario` y la consulta degradada usa `Cuales son las medidas iniciales ante una crisis hipertensiva?`.
- Tres preguntas oficiales del enunciado: `docs/services/clinical-assistant-service.md:211-219`. Las cuatro sondas PROTO-003 se evalúan por separado (4 %, 6,5 %, 9 % y alteración del estado mental), según `docs/adr/ADR-05-assistant-llm-provider.md:145`.
- Negativa: `¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?` → `fuentes=[]`, aviso `no existe protocolo disponible`, `modelo_usado=none`.
- Todos los identificadores de paciente son sintéticos. Ningún reporte incluye nombre, queja ni texto clínico del paciente; solo UUID, nivel, SLA, estado y `traceparent`.

## 6. Criterios de entrada y salida

**Entrada.** Rama y commit identificados; sin ejecución activa de `deploy-azure`; secretos y variables presentes con los mismos nombres; `kubectl` y `kubelogin` disponibles; para Azure, kubeconfig de `vidacruz-aks`; para local, contexto `docker-desktop` y `127.0.0.1`.

**Salida.** Todas las suites Gradle en verde; 33/33 en el E2E local; Newman 31/70 sin fallos; R-06 13/13; chat 7 sondas × 5/5; verificación row-level sin filas `requiere_revision`; reportes sanitizados y commiteados; ADR-11/ADR-05 sin cambiar de estado.

**Suspensión.** Cualquier fallo de aplicación (no de harness), pérdida de conectividad con el proveedor LLM sin reintento acotado, o respuesta `503` sostenida después de tres reintentos.

## 7. Compuertas y métricas

| Compuerta | Umbral | Estado |
|---|---|---|
| Suites unitarias/integración | 0 fallos | Verde |
| E2E local | 33/33 | Verde (`local-e2e-main-final2.txt`) |
| Newman en Azure | 31 peticiones / 70 aserciones / 0 fallos | Verde tras rerun limpio |
| R-06 en Azure | 13/13 | Verde |
| Gate de chat | 5 corridas por sonda, 7 sondas | Verde (35/35) |
| Row-level en Azure | Fila de paciente, outbox, auditoría y alerta coherentes | Verde |
| Coste medido (ADR-05) | Medición real del proveedor | **Abierto** |
| Spike completo (ADR-05) | Llamadas reales en todos los nodos | **Abierto** |
| Secretos/imagen (ADR-11) | Rotación y escaneo registrados | **Abierto** |

## 8. Ambientes y herramientas

| Entorno | Acceso | Herramientas |
|---|---|---|
| Local Compose | `127.0.0.1` | Docker Compose, `curl` |
| Kubernetes local | Contexto `docker-desktop`, namespace `vidacruz` | `kubectl`, PowerShell 7, Jaeger en `127.0.0.1:18087` |
| Azure | Host `https://vidacruz-l7rsr6.northcentralus.cloudapp.azure.com` | `kubectl` con kubeconfig de AKS, `curl.exe`, PowerShell 7, OIDC/Entra ID |
| CI | GitHub Actions | Java 21, Gradle, kustomize 5.6.0, kubeconform 0.7.0 |

## 9. Resultados y artefactos

| Entorno | Artefacto |
|---|---|
| Local | `docs/audits/20261128/local-e2e-main-final2.txt` (33/33) |
| Azure API | `docs/audits/20261128/azure-r6-clean-rerun.txt` (13/13) |
| Azure chat | `docs/audits/20261128/azure-chat-five-runs.txt` (35/35) |
| Azure BD | `docs/audits/20261128/azure-db-rowlevel-verification.txt` |
| Azure smoke y presupuesto | `docs/audits/20261128/azure-v106-verification.md`, `azure-budget-alert-export.json` |
| Procedimiento | `docs/operations/test-execution-plan.md` |

Los reportes se sanitizan antes de commitear: sin valores de API key, contraseñas, tokens, correos ni id de suscripción. El JSON crudo de Newman **no se commitea** porque contiene las claves reales de la colección; solo se registra el resumen de peticiones/aserciones/fallos.

## 10. Limitaciones y deuda conocida

1. No hay Jaeger en Azure: la traza compartida entre servicios solo se verificó en local.
2. El firewall de PostgreSQL solo admite la IP de salida fija de AKS (`infra/azure/main.bicep:261-268`); la verificación row-level se hace desde un pod dentro del clúster, no desde la estación de trabajo.
3. El harness canónico `scripts/verify-local-k8s.ps1` ya incorpora las correcciones de robustez (fixtures en español, `correlationId` desde el cuerpo JSON, reintento del outbox, 600 mensajes de Kafka, timeout de 150 s y salida natural de `curl` para el SSE); `scripts/verify-local-k8s-e2e.ps1` se conserva como alias histórico.
4. Tres respuestas `503` transitorias del proveedor en la sonda PROTO-003 del 6,5 % se gestionaron con reintento acotado; quedaron registradas, no ocultas.
5. RF-16 es una etiqueta del harness, no un requisito canónico del enunciado.

## 11. Estado de decisiones

ADR-11 y ADR-05 siguen `Proposed`. La evidencia ejecutada cierra las compuertas de reproducibilidad, alertas de presupuesto, filas en BD y cinco corridas por sonda, pero no sustituye la aceptación formal de ningún ADR.
