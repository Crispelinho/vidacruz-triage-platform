# Plan de ejecución de pruebas — VidaCruz Triage Platform

> Complemento ejecutable de `docs/operations/test-plan.md`. Repite, en este orden, todo lo que se ejecutó sobre `main` `38d9d77` y sobre `develop` `f4e17ab`/`2fbb06c` el 2026-11-28. Cada etapa indica el comando, el resultado esperado y el artefacto que debe quedar guardado.

## 1. Prerrequisitos

| Requisito | Valor / comprobación |
|---|---|
| Java | 21 |
| PowerShell | 7 (`pwsh`), no Windows PowerShell 5.1 para JSON inline |
| Docker | Compose y Desktop Kubernetes con contexto `docker-desktop` |
| kubectl / kubelogin | En `PATH` para Azure; `--context docker-desktop` para local |
| kubeconfig de Azure | Ruta local a `vidacruz-aks`; nunca se commitea |
| Host público | `https://vidacruz-l7rsr6.northcentralus.cloudapp.azure.com` |
| Secretos | Nombres y valores existentes en GitHub/AKS; **no se renombran ni se imprimen** |
| Colección Postman | Local: `postman/vidacruz-local-demo.postman_collection.json` + `postman/vidacruz-local.postman_environment.json`. Azure: colección exportada sin valores; **no versionar con claves reales** |
| Concurrencia | Ninguna ejecución activa de `deploy-azure` (`deploy.yml:21-23`) |

## 2. Orden de ejecución

```text
Etapa 0  Preparación y estado del repositorio
Etapa 1  Unidad + integración (Gradle)
Etapa 2  Verificaciones de configuración y proveedores
Etapa 3  E2E con Docker Compose
Etapa 4  E2E en Kubernetes local (33 comprobaciones)
Etapa 5  Verificación en vivo en Azure (API, chat, BD)
Etapa 6  Captura, sanitización y cierre
```

No ejecutar `infra/azure/provision.ps1` ni `deploy.yml` sin revisar previamente SKU, OIDC, nombres de secretos y ausencia de runs activos.

## 3. Etapa 0 — Preparación

```powershell
git fetch cris develop main
git log -1 --format='%H %s' cris/develop
git log -1 --format='%H %s' cris/main
git merge-base --is-ancestor cris/main cris/develop   # 0 = back-merge presente
gh run list --repo Crispelinho/vidacruz-triage-platform --event workflow_dispatch --status in_progress
```

Último estado verificado: `cris/main=38d9d77`, `cris/develop=2fbb06c`, back-merge `dd10584` presente, cero runs activos.

## 4. Etapa 1 — Unidad e integración

```bash
cd triage-service/triage-service && ./gradlew build --no-daemon
cd clinical-assistant-service/clinical-assistant-service && ./gradlew build --no-daemon
```

Es lo que ejecutan `.github/workflows/triage-service.yml:41` y `.github/workflows/clinical-assistant-service.yml:41`. Cubre RN-01/RN-02, VAL-01…VAL-11, TST-RXN (BlockHound), TST-IDM, TST-PRJ, TST-RPT y las integraciones con Testcontainers. Resultado esperado: `BUILD SUCCESSFUL`, cero fallos, XML de Surefire sin `failures`.

## 5. Etapa 2 — Verificaciones de configuración y proveedores

Solo con la clave real correspondiente, disponible como secreto del repositorio:

```bash
./gradlew test --no-daemon --tests 'com.vidacruz.clinical.integration.GeminiEmbeddingVerificationTest'
./gradlew test --no-daemon --tests 'com.vidacruz.clinical.integration.OpenRouterChatVerificationTest'
```

Definido en `gemini-embedding-verification.yml:43` y `openrouter-chat-verification.yml:44`. Resultado esperado: calibración TST-RAG y evaluación de proveedor completas, `results.json` subido como artefacto. El coste **no** se mide aquí.

## 6. Etapa 3 — E2E con Docker Compose

```bash
docker compose up -d --build
docker compose ps
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8082/actuator/health
curl -fsS -o /dev/null http://localhost:16686/
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
```

Las dos respuestas de health deben ser `{"status":"UP"}` y Jaeger `200`. Importar `postman/vidacruz-local.postman_environment.json` y luego `postman/vidacruz-local-demo.postman_collection.json` (`local-verification.md:21-23`). API keys locales: `test-triage-key` y `test-assistant-key`.

## 7. Etapa 4 — E2E en Kubernetes local (33 comprobaciones)

```powershell
docker build -t triage-service:local ./triage-service/triage-service
docker build -t clinical-assistant-service:local ./clinical-assistant-service/clinical-assistant-service
kubectl --context docker-desktop apply -k ./k8s/local
kubectl --context docker-desktop -n vidacruz rollout status statefulset/local-db
kubectl --context docker-desktop -n vidacruz rollout status statefulset/kafka
kubectl --context docker-desktop -n vidacruz rollout status statefulset/redis
kubectl --context docker-desktop -n vidacruz wait --for=condition=complete job/kafka-topic-init --timeout=5m
kubectl --context docker-desktop -n vidacruz rollout status deployment/triage-service
kubectl --context docker-desktop -n vidacruz rollout status deployment/clinical-assistant-service
kubectl --context docker-desktop -n vidacruz rollout status deployment/jaeger
kubectl --context docker-desktop -n vidacruz port-forward svc/triage-service 18086:8081
kubectl --context docker-desktop -n vidacruz port-forward svc/clinical-assistant-service 18085:8082
kubectl --context docker-desktop -n vidacruz port-forward svc/jaeger 18087:16686
pwsh -NoProfile -File ./scripts/verify-local-k8s-e2e.ps1 -ReportPath ./local-e2e.txt
```

Usar siempre `127.0.0.1`, nunca `localhost` (`local-kubernetes-e2e.md:39-42`). Las dos implementaciones deben estar a `2/2`. El harness tarda unos dos minutos por la espera del SLA y del latido SSE.

Resultado esperado: `TOTAL=33 PASS=33 FAIL=0`. Cobertura: salud (3), clasificación E1-E6 (6), ciclo de estados (5), RF-16/RF-04/RF-03 (3), SSE y reconocimientos (4), reportes (2), modo degradado (2), auth (2), métricas (2), outbox→Kafka (3), traza compartida en Jaeger (1).

**Usar `scripts/verify-local-k8s-e2e.ps1`**, no el original `scripts/verify-local-k8s.ps1`. La versión corregida incorpora: fixtures españolas, lectura del `correlationId` desde el cuerpo JSON cuando no viene en headers, reintento de la consulta al outbox, consumo de hasta 600 mensajes de Kafka y timeout de 150 s para el camino degradado.

## 8. Etapa 5 — Verificación en vivo en Azure

### 8.1 Smoke HTTP

Comprobar las 16 URL de Swagger/OpenAPI/WebJars y `/ops/{triage,asistente}/health/readiness` (`azure-v106-verification.md:81`). Todas deben devolver `200`; la documentación y la salud pública no requieren API key.

### 8.2 Newman

```powershell
npx newman run ./azure-collection.no-secrets.json `
  --env-var "baseUrl=https://vidacruz-l7rsr6.northcentralus.cloudapp.azure.com" `
  --env-var "triageApiKey=$env:TRIAGE_API_KEY" `
  --env-var "asistenteApiKey=$env:ASSISTANT_API_KEY" `
  --reporters cli,json --reporter-json-export ./newman.json
```

La colección Azure no está versionada en esta rama porque la copia utilizada guarda las claves reales en sus variables; exportarla sin valores antes de ejecutar y pasar las credenciales solo por `--env-var`. Resultado esperado: 31 peticiones, 70 aserciones, 0 fallos. **No commitear `newman.json` ni la colección con valores**: commitear solo el resumen de conteos.

### 8.3 R-06 (13 comprobaciones)

```powershell
pwsh -NoProfile -File ./scripts/verify-azure-r6.ps1 `
  -Kubeconfig '<ruta-al-kubeconfig>' `
  -PublicHost 'https://vidacruz-l7rsr6.northcentralus.cloudapp.azure.com' `
  -ReportPath ./azure-r6.txt
```

El script lee las API keys de los Secrets de Kubernetes con `kubectl get secret`, nunca las imprime, y escribe un reporte sanitizado. Resultado esperado: 13 PASS / 0 FAIL (health, registro con `traceparent`, escalado SLA, reconocimientos `400`/`200`/`409`, SSE snapshot/latido/alta/resolución, transición a `EN_CONSULTA`, chat y outbox→Kafka).

Si aparece `400` en todos los POST autenticados, es un defecto de codificación de PowerShell 5.1: escribir el JSON como UTF-8 sin BOM en un archivo temporal y pasar `curl.exe --data-binary @archivo`.

### 8.4 Gate de chat (7 sondas × 5 corridas)

```powershell
pwsh -NoProfile -File ./scripts/verify-azure-chat-five-runs.ps1 `
  -Kubeconfig '<ruta-al-kubeconfig>' `
  -PublicHost 'https://vidacruz-l7rsr6.northcentralus.cloudapp.azure.com' `
  -ReportPath ./azure-chat-five-runs.txt
```

Cinco llamadas por cada sonda: PROTO-001, PROTO-002, cuatro variantes de PROTO-003 (4 %, 6,5 %, 9 % y alteración del estado mental) y la negativa de diabetes. Resultado esperado: `TOTAL=7 PASS=7 FAIL=0`, es decir 35/35 corridas. Cada positiva debe traer `200`, respuesta no vacía, el protocolo esperado en `fuentes` y `modelo_usado=gemini/gemini-3.5-flash-lite`. La negativa debe traer `fuentes=[]`, `no existe protocolo disponible` y `modelo_usado=none`.

El harness reintenta solo `503` con backoff acotado (3 intentos, 3/6/9 s) y registra el estado final de cada corrida. No ocultar los `503` transitorios.

### 8.5 Row-level en PostgreSQL

```powershell
pwsh -NoProfile -File ./scripts/verify-azure-db-rowlevel.ps1 `
  -Kubeconfig '<ruta-al-kubeconfig>' -ReportPath ./azure-db-rowlevel.txt
```

El firewall solo admite la IP de salida fija de AKS (`infra/azure/main.bicep:261-268`, `159-166`), así que el script crea un pod temporal en `vidacruz` que sale por esa IP. El pod corre bajo PodSecurity `restricted`: non-root (uid 70), `allowPrivilegeEscalation=false`, `capabilities.drop=["ALL"]`, `seccompProfile=RuntimeDefault`. `PGUSER` se lee del ConfigMap y `PGPASSWORD` del Secret mediante `secretKeyRef`; ninguno se imprime. Solo `SELECT`. El pod se borra al finalizar.

| Comprobación | Resultado esperado |
|---|---|
| Paciente de verificación | `CRITICO`, `EN_CONSULTA`, `sla_minutos=5` |
| Outbox del paciente | `paciente_registrado` con `traceparent`, `paciente_escalado` sin trace (origen timer), `paciente_en_consulta` con el suyo; todos publicados, `intentos=0`, `requiere_revision=false` |
| Coincidencias del trace | `paciente_registrado` para el paciente verificado y el vecino |
| Auditoría | `REGISTRO → ESCALAMIENTO → ENTRADA_CONSULTA` |
| Alerta | `RESUELTA`, `minutos_espera=5.03`, reconocida y resuelta |
| Filas que requieren revisión | 0 |

## 9. Etapa 6 — Captura y sanitización

Antes de commitear cualquier reporte:

1. Sustituir valores de API key, contraseñas y tokens por `<redacted>`.
2. Quitar correos y el id de suscripción (ver `azure-budget-alert-export.json`).
3. Mantener solo UUID sintéticos, niveles, estados, SLA, `traceparent` y marcas de tiempo.
4. No incluir nombres ni quejas de pacientes.
5. No incluir el JSON crudo de Newman con la colección cargada.

Reportes que deben quedar en el repositorio: `local-e2e-main-final2.txt`, `azure-r6-clean-rerun.txt`, `azure-chat-five-runs.txt` y `azure-db-rowlevel-verification.txt`, todos en `docs/audits/20261128/`.

## 10. Matriz de ejecución

| Escenario | Comando / etapa | Resultado esperado | Evidencia |
|---|---|---|---|
| E1-E6, RN-01 | Etapa 4 | 6/6 niveles correctos | `local-e2e-main-final2.txt:7-12` |
| VAL-01…VAL-11 | Etapa 1 | 0 fallos | XML de Surefire |
| RF-02, TST-STA | Etapas 3 y 4 | Transiciones válidas; 409 inválida | E2E local RF-02 |
| RF-03, RN-03, TST-ESC | Etapas 4, 8.3 y 8.5 | Un escalado; alerta única | `azure-db-rowlevel-verification.txt:28-32` |
| RF-04, RN-05, TST-RAG | Etapas 2, 8.2 y 8.4 | Citas válidas; aviso fijo | `azure-chat-five-runs.txt` |
| RF-05, TST-RPT | Etapas 1 y 4 | Conteos, media y p95 | `OperationalReportsIntegrationTest` |
| RF-06, EVT-01…04 | Etapas 4, 8.3 y 8.5 | SSE completo; eventos publicados | `azure-r6-clean-rerun.txt`, `azure-db-rowlevel-verification.txt:19-32` |
| NFR-03/04, TST-RES | Etapa 4 | `503 LLM_UNAVAILABLE`, Triage sigue sirviendo | E2E local caso degradado |
| NFR-05, NFR-10 | Etapas 3, 8.1 y 8.2 | Health `UP`; 401 sin clave | 16/16 URLs; Newman |
| NFR-16 | Etapa 4 y 8.3 | Jaeger local con ambos servicios; `traceparent` en Azure | `local-e2e-main-final2.txt`; `azure-r6-clean-rerun.txt` |
| NFR-13/14 | Etapa 8 y `k8s-manifests.yml` | Manifiestos válidos; deploy limpio | `azure-v106-verification.md:79-81` |
| Presupuesto | `az consumption budget show` | Alertas 50 %/80 % `enabled` | `azure-budget-alert-export.json` |

## 11. Resolución de problemas

| Síntoma | Causa | Acción |
|---|---|---|
| `400` en todos los POST autenticados | Codificación inline de PowerShell 5.1 | JSON UTF-8 sin BOM en archivo + `curl.exe --data-binary @archivo` |
| `violates PodSecurity "restricted:latest"` | Pod sin contexto de seguridad | Añadir non-root, capabilities dropadas y `seccompProfile: RuntimeDefault` |
| `psql` con `Connection timed out` | Firewall solo permite la IP de egress de AKS | Ejecutar desde un pod dentro del clúster; no abrir el firewall |
| `503` intermitente del proveedor LLM | Transitorio del proveedor | Reintentar solo `503`, hasta tres veces con backoff; registrar los intentos |
| Correlación no encontrada tras 401 | El id viene en el cuerpo, no en headers | Leer también `Json.correlationId` |
| Outbox sin fila todavía | Escritura asíncrona | Reintentar la consulta hasta que aparezca |
| Kafka no alcanza el evento esperado | El tópico acumula corridas anteriores | Consumir hasta 600 mensajes |
| Timeout en modo degradado | El proveedor local tarda ~97 s en caer | Timeout de 150 s |
| E5 clasifica mal | Fixture en inglés frente a clasificador español | Usar `Renovacion de recetario` |

## 12. Checklist de cierre

- [ ] Suites Gradle en verde
- [ ] 33/33 en el E2E local
- [ ] 16/16 URL del smoke en Azure
- [ ] Newman 31/70 sin fallos (resumen guardado, JSON crudo no commiteado)
- [ ] R-06 13/13
- [ ] Chat 35/35 con los `503` registrados
- [ ] Row-level coherente y pod temporal eliminado
- [ ] Reportes sanitizados y commiteados
- [ ] ADR-11 y ADR-05 continúan `Proposed`
- [ ] Compuertas abiertas explícitamente documentadas: coste, spike completo, secretos/imagen
